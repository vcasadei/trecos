package app.trecos.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Drive client against a local JDK HTTP server (task 12.4), with errors and retries. */
class DriveClientTest {

    /** One request the server saw. */
    private data class Seen(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: ByteArray)

    /** A scripted answer: status and body. */
    private data class Answer(val status: Int, val body: String = "")

    private lateinit var server: HttpServer
    private val seen = CopyOnWriteArrayList<Seen>()
    private val answers = ArrayDeque<Answer>()
    private val waits = mutableListOf<Long>()
    private var failNetworkTimes = 0
    private lateinit var client: DriveClient

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        client = DriveClient(token = { "tok" }, baseUrl = "http://127.0.0.1:${server.address.port}", attempts = 4, backoffMs = 100, sleep = { waits += it })
    }

    @After
    fun stop() = server.stop(0)

    /** Records the request and plays the next answer. */
    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes()
        seen += Seen(
            exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.rawQuery,
            exchange.requestHeaders.mapValues { it.value.joinToString() }.mapKeys { it.key.lowercase() }, body,
        )
        if (failNetworkTimes > 0) {
            failNetworkTimes--
            exchange.close()
            return
        }
        val answer = synchronized(answers) { answers.removeFirstOrNull() } ?: Answer(200, "{}")
        val bytes = answer.body.toByteArray()
        exchange.sendResponseHeaders(answer.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    /** Queues answers. */
    private fun answer(vararg list: Answer) = synchronized(answers) { answers.addAll(list) }

    @Test
    fun listsEveryPageWithTheToken() = runBlocking {
        answer(
            Answer(200, """{"files":[{"id":"1","name":"a"}],"nextPageToken":"p2"}"""),
            Answer(200, """{"files":[{"id":"2","name":"b","mimeType":"application/vnd.google-apps.folder","parents":["root"],"extra":true}]}"""),
        )
        val files = client.list("name = 'Trecos'")

        assertEquals(listOf("1", "2"), files.map { it.id })
        assertEquals(DriveClient.FOLDER, files[1].mimeType)
        assertEquals("Bearer tok", seen[0].headers["authorization"])
        assertTrue(seen[0].query!!.contains("q=name%20%3D%20%27Trecos%27"))
        assertTrue(seen[1].query!!.contains("pageToken=p2"))
    }

    @Test
    fun createsFoldersAndUploadsFiles() = runBlocking {
        answer(Answer(200, """{"id":"f1","name":"Trecos","mimeType":"application/vnd.google-apps.folder"}"""), Answer(200, """{"id":"x","name":"a.bin"}"""))
        assertEquals("f1", client.createFolder("Trecos", null).id)
        assertTrue(seen[0].body.decodeToString().contains("\"mimeType\":\"application/vnd.google-apps.folder\""))

        val file = client.upload("a.bin", "f1", byteArrayOf(1, 2, 3))
        assertEquals("x", file.id)
        val upload = seen[1]
        assertEquals("/upload/drive/v3/files", upload.path)
        assertTrue(upload.query!!.contains("uploadType=multipart"))
        assertTrue(upload.headers["content-type"]!!.startsWith("multipart/related; boundary="))
        val body = upload.body.decodeToString(throwOnInvalidSequence = false)
        assertTrue(body.contains("\"parents\":[\"f1\"]"))
    }

    @Test
    fun updatesWithThePatchOverrideAndDownloads() = runBlocking {
        answer(Answer(200, "{}"), Answer(200, "hello"))
        client.update("x", "new".toByteArray())
        assertEquals("POST", seen[0].method)
        assertEquals("PATCH", seen[0].headers["x-http-method-override"])
        assertArrayEquals("new".toByteArray(), seen[0].body)

        assertEquals("hello", client.download("x").decodeToString())
        assertTrue(seen[1].query!!.contains("alt=media"))
    }

    @Test
    fun readsTheUser() = runBlocking {
        answer(Answer(200, """{"user":{"displayName":"Vitor","emailAddress":"v@example.com"}}"""))
        assertEquals(DriveUser("Vitor", "v@example.com"), client.user())
    }

    @Test
    fun retriesServerErrorsAndRateLimitsWithBackoff() = runBlocking {
        answer(Answer(503), Answer(429), Answer(500), Answer(200, """{"files":[]}"""))
        assertTrue(client.list("x").isEmpty())
        assertEquals(4, seen.size)
        assertEquals(listOf(100L, 200L, 400L), waits)
    }

    @Test
    fun retriesNetworkErrors() = runBlocking {
        failNetworkTimes = 2
        answer(Answer(200, "data"))
        assertEquals("data", client.download("x").decodeToString())
        assertTrue(seen.size >= 3)
    }

    @Test
    fun givesUpAfterTheLastAttempt() {
        answer(Answer(503), Answer(503), Answer(503), Answer(503))
        val error = assertThrows(DriveException::class.java) { runBlocking { client.download("x") } }
        assertEquals(503, error.code)
        assertEquals(4, seen.size)
    }

    @Test
    fun clientErrorsAreNotRetried() {
        answer(Answer(401, "expired"))
        assertThrows(DriveAuthException::class.java) { runBlocking { client.download("x") } }
        answer(Answer(404))
        assertThrows(DriveNotFoundException::class.java) { runBlocking { client.download("y") } }
        answer(Answer(403, "forbidden"))
        val forbidden = assertThrows(DriveException::class.java) { runBlocking { client.download("z") } }
        assertEquals(403, forbidden.code)
        assertEquals(3, seen.size)
    }

    @Test
    fun deletingAMissingFileIsFine() = runBlocking {
        answer(Answer(404))
        client.delete("gone")
        assertEquals("DELETE", seen.single().method)
    }

    @Test
    fun networkFailureEveryTimeIsAnIoException() {
        failNetworkTimes = 100
        assertThrows(IOException::class.java) { runBlocking { client.download("x") } }
        // The JDK's own HttpURLConnection silently retries a dropped GET once, so count our tries by their waits.
        assertEquals(listOf(100L, 200L, 400L), waits)
    }
}
