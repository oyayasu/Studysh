package com.oyayasu.studysh

import com.oyayasu.studysh.supertonic.*
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class SupertonicModelStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val bytes = ByteArray(150_000) { (it % 251).toByte() }
    private fun asset() = ModelAsset("onnx/model.onnx", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

    private fun withServer(ignoreRange: Boolean = false, corrupt: Boolean = false, block: (ModelStore, () -> Int) -> Unit) {
        var requests = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/onnx/model.onnx") { exchange ->
            requests++
            val start = if (ignoreRange) 0 else exchange.requestHeaders.getFirst("Range")
                ?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            val payload = (if (corrupt) ByteArray(bytes.size) { 42 } else bytes).copyOfRange(start, bytes.size)
            if (start > 0) exchange.responseHeaders.add("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
            exchange.sendResponseHeaders(if (start > 0) 206 else 200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try { block(ModelStore(temp.root, "http://127.0.0.1:${server.address.port}/")) { requests } }
        finally { server.stop(0) }
    }

    @Test fun downloadedBytesAreVerifiedAndReusedWithoutNetwork() = withServer { store, requests ->
        var last: ModelStore.Progress? = null
        store.install(listOf(asset())) { last = it }
        assertArrayEquals(bytes, File(temp.root, asset().path).readBytes())
        assertEquals(bytes.size.toLong(), last!!.downloaded)
        assertEquals(setOf(asset().path), store.verifiedPaths(listOf(asset())))
        store.install(listOf(asset()))
        assertEquals(1, requests())
    }
    @Test fun interruptedTransferResumesAndInstallsAtomically() = withServer { store, _ ->
        var cancel = false
        assertThrows(CancellationException::class.java) {
            store.install(listOf(asset()), { cancel }) { cancel = it.downloaded > 0 }
        }
        assertFalse(File(temp.root, asset().path).exists())
        assertTrue(File(temp.root, asset().path + ".part").length() > 0)
        store.install(listOf(asset()))
        assertArrayEquals(bytes, File(temp.root, asset().path).readBytes())
        assertFalse(File(temp.root, asset().path + ".part").exists())
    }
    @Test fun serversIgnoringRangeRestartCleanly() = withServer(ignoreRange = true) { store, _ ->
        File(temp.root, asset().path + ".part").apply { parentFile!!.mkdirs(); writeBytes(bytes.copyOf(1000)) }
        store.install(listOf(asset()))
        assertArrayEquals(bytes, File(temp.root, asset().path).readBytes())
    }
    @Test fun sameSizeCorruptionIsDetectedAndRepaired() = withServer { store, _ ->
        File(temp.root, asset().path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes.size)) }
        assertTrue(store.verifiedPaths(listOf(asset())).isEmpty())
        store.install(listOf(asset()))
        assertEquals(setOf(asset().path), store.verifiedPaths(listOf(asset())))
    }
    @Test fun corruptDownloadsAreNeverMarkedInstalled() = withServer(corrupt = true) { store, requests ->
        assertThrows(IOException::class.java) { store.install(listOf(asset())) }
        assertEquals(3, requests())
        assertFalse(File(temp.root, asset().path).exists())
        assertFalse(File(temp.root, asset().path + ".part").exists())
    }
}
