package app.trecos.ui.text

/**
 * Worst-case strings for layout tests: every list, bar and header must hold
 * them without breaking.
 */
object StressFixtures {
    /** An item name of exactly 200 characters, with spaces. */
    val longName: String = ("Cabo USB-C para USB-A trançado de nylon com carregamento rápido " +
        "e transferência de dados, dois metros, cor cinza espacial, comprado em 2024 para o notebook do escritório de casa")
        .padEnd(200, 'x').take(200)

    /** A serial number of 40 characters with no spaces. */
    const val longSerial: String = "SN4C8E2F9A7B1D3E5F7A9C2E4B6D8F0A1C3E5G7K"

    /** A path eight levels deep, from the house down. */
    val deepPath: List<String> = listOf(
        "Casa dos meus pais", "Escritório", "Armário grande", "Prateleira de cima",
        "Caixa organizadora", "Box A", "Saquinho de adaptadores", "Cables bag",
    )

    /** Portuguese labels, which run longer than their English versions. */
    val portuguese: List<String> = listOf(
        "Configurações de sincronização", "Itens sem preço definido", "Adicionar à lixeira",
    )
}
