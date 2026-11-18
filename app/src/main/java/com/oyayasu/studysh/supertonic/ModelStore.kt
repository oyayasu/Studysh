package com.oyayasu.studysh.supertonic

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Resumable, atomic and checksum-verified model delivery. Only this class uses a network. */
class ModelStore(val root: File, private val baseUrl: String = SupertonicAssets.BASE_URL) {
    data class Progress(val path: String, val downloaded: Long, val total: Long)

    fun verifiedPaths(assets: List<ModelAsset>, cancelled: () -> Boolean = { false }): Set<String> =
        assets.filter { valid(File(root, it.path), it, cancelled) }.map { it.path }.toSet()

    fun install(
        assets: List<ModelAsset>,
        cancelled: () -> Boolean = { false },
        progress: (Progress) -> Unit = {},
    ) {
        val total = assets.sumOf { it.bytes }
        var completed = 0L
        for (asset in assets) {
            checkActive(cancelled)
            val target = File(root, asset.path)
            if (!valid(target, asset, cancelled)) {
                target.parentFile?.mkdirs()
                val part = File(target.path + ".part")
                var last: IOException? = null
                for (attempt in 1..3) {
                    try {
                        if (part.length() > asset.bytes) part.delete()
                        if (part.length() != asset.bytes) {
                            transfer(asset, part, cancelled) { bytes ->
                                progress(Progress(asset.path, completed + bytes, total))
                            }
                        }
                        if (!valid(part, asset, cancelled)) {
                            part.delete()
                            throw IOException("Size or SHA-256 mismatch: ${asset.path}. Please retry the download.")
                        }
                        if (!part.renameTo(target)) throw IOException("Cannot install ${asset.path}")
                        last = null
                        break
                    } catch (e: IOException) { last = e }
                }
                last?.let { throw it }
            }
            completed += asset.bytes
            progress(Progress(asset.path, completed, total))
        }
    }

    private fun transfer(asset: ModelAsset, part: File, cancelled: () -> Boolean, progress: (Long) -> Unit) {
        checkActive(cancelled)
        val existing = part.length()
        val connection = (URL(baseUrl + asset.path + "?download=true").openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            val resume = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resume) {
                if (code == 416) part.delete()
                throw IOException("HTTP $code while downloading ${asset.path}")
            }
            val start = if (resume) existing else 0L
            if (resume && connection.getHeaderField("Content-Range").orEmpty() != "bytes $start-${asset.bytes - 1}/${asset.bytes}") {
                part.delete()
                throw IOException("Invalid resumed download range: ${asset.path}")
            }
            val length = connection.contentLengthLong
            if (length >= 0 && length != asset.bytes - start) {
                throw IOException("Unexpected download size: ${asset.path} ($length bytes)")
            }
            FileOutputStream(part, resume).use { output ->
                connection.inputStream.use { input ->
                    var done = start
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkActive(cancelled)
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (done + count > asset.bytes) throw IOException("Download exceeded expected size: ${asset.path}")
                        output.write(buffer, 0, count)
                        done += count
                        progress(done)
                    }
                }
            }
        } finally { connection.disconnect() }
    }

    private fun valid(file: File, asset: ModelAsset, cancelled: () -> Boolean): Boolean {
        if (!file.isFile || file.length() != asset.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive(cancelled)
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == asset.sha256
    }

    private fun checkActive(cancelled: () -> Boolean) {
        if (cancelled()) throw CancellationException("Download cancelled")
    }
}
