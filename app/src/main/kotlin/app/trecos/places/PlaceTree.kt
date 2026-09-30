package app.trecos.places

import app.trecos.data.Container
import app.trecos.data.ContainerTotal

/**
 * A container's value as shown.
 *
 * @property value the value in minor units: the override, or the sum of everything below.
 * @property manual whether the value is a manual override.
 * @property unpriced how many items at any depth below have no price.
 * @property items how many items sit at any depth below.
 */
data class PlaceValue(val value: Long, val manual: Boolean, val unpriced: Int, val items: Int = 0)

/**
 * The container tree of one house, with values folded bottom-up in memory
 * (design D5).
 *
 * @param containers every container of the house.
 * @param totals the direct item totals per container (and the top level, keyed `null`).
 */
class PlaceTree(containers: List<Container>, totals: List<ContainerTotal>) {

    private val byId = containers.associateBy { it.id }
    private val children = containers.groupBy { it.parentId }
    private val direct = totals.associateBy { it.containerId }
    private val values = HashMap<String, PlaceValue>()
    private val visiting = HashSet<String>()

    /** The house's value: the top level's items plus every top-level container. */
    val houseValue: PlaceValue by lazy { fold(null, override = null) }

    /**
     * @param id a container id.
     * @return the container's value, or `null` for an unknown id.
     */
    fun value(id: String): PlaceValue? {
        val container = byId[id] ?: return null
        return values.getOrPut(id) { fold(id, container.valueOverride) }
    }

    /**
     * Returns the chain of containers from the top level down to [id], inclusive.
     *
     * @param id a container id.
     * @return the containers from the house's top level to [id]; empty for an unknown id.
     */
    fun path(id: String?): List<Container> {
        val chain = ArrayList<Container>()
        val seen = HashSet<String>()
        var current = id?.let(byId::get)
        while (current != null && seen.add(current.id)) {
            chain += current
            current = current.parentId?.let(byId::get)
        }
        return chain.asReversed()
    }

    /**
     * Finds the colour a container shows: its own, or the nearest ancestor's.
     *
     * @param id a container id.
     * @return the palette colour key, or `null` when neither it nor any ancestor has one.
     */
    fun colorKey(id: String?): String? = path(id).lastOrNull { it.colorKey != null }?.colorKey

    /**
     * @param parentId a container id, or `null` for the top level.
     * @return how many containers sit directly inside it.
     */
    fun childCount(parentId: String?): Int = children[parentId].orEmpty().size

    private fun fold(id: String?, override: Long?): PlaceValue {
        val own = direct[id]
        var sum = own?.total ?: 0L
        var unpriced = own?.unpriced ?: 0
        var items = own?.items ?: 0
        if (id != null && !visiting.add(id)) return PlaceValue(0, manual = false, unpriced = 0, items = 0)
        for (child in children[id].orEmpty()) {
            val childValue = value(child.id) ?: continue
            sum += childValue.value
            unpriced += childValue.unpriced
            items += childValue.items
        }
        id?.let(visiting::remove)
        return PlaceValue(override ?: sum, manual = override != null, unpriced = unpriced, items = items)
    }
}
