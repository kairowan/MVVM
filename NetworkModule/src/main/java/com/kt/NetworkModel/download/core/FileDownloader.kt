package com.kt.NetworkModel.download.core

import android.util.Log
import com.kt.NetworkModel.App
import com.kt.NetworkModel.download.exceptions.HttpException
import com.kt.NetworkModel.download.exceptions.RetryException
import com.kt.NetworkModel.download.listener.DownloadListener
import com.kt.NetworkModel.download.model.DownloadConfig
import com.kt.NetworkModel.download.utils.FileUtils
import com.kt.NetworkModel.download.utils.FileUtils.createTempFileForDownload
import com.kt.NetworkModel.download.utils.FileUtils.getOriginalFilename
import com.kt.NetworkModel.download.utils.PathUtils.HttpClient
import com.kt.NetworkModel.download.utils.PathUtils.ValidationUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.UUID
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.resumeWithException

/**
 * @author 浩楠
 *
 * @date 2025/4/4-15:41
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO
 */

class FileDownloader private constructor(
    private val config: DownloadConfig
) {
    companion object {
        fun create(config: DownloadConfig = DownloadConfig.global): FileDownloader {
            return FileDownloader(config)
        }
    }

    private val mutex = Mutex()

    suspend fun execute(
        url: String,
        savePath: String,
        listener: DownloadListener,
        enableResume: Boolean
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // 验证 URL 和路径
            ValidationUtils.validateUrl(url, config.forceHttps)
            ValidationUtils.validatePath(savePath, config.allowedBasePath.toString())
            // 生成唯一 ID
            val downloadId = generateDownloadId(url)
            // 获取初始响应并提取原始文件名
            val initialResponse = try {
                fetchResponse(url, 0L)
            } catch (e: Exception) {
                listener.onError(downloadId, IOException("Failed to get initial response: ${e.message}"))
                throw e
            }
            val originalFilename = getOriginalFilename(initialResponse)
            var (tempFile, targetFile) = createTempFileForDownload(App.get(), originalFilename)
            var retryCount = 0
            while (retryCount <= config.maxRetries) {
                try {
                    val downloadedBytes = handleResume(tempFile, enableResume)
                    val response = fetchResponse(url, downloadedBytes)
                    // 处理响应并通知进度
                    processResponse(response, tempFile, savePath, listener, downloadedBytes, downloadId)
                    // 下载完成，重命名临时文件为目标文件
                    finalizeDownload(tempFile, targetFile)
                    listener.onSuccess(downloadId, targetFile)
                    return@withContext
                } catch (e: Exception) {
                    val shouldRetry = handleRetry(e, retryCount, tempFile, savePath, listener, downloadId)
                    if (shouldRetry) {
                        retryCount++
                        tempFile.delete()
                        val (newTempFile, newTargetFile) = createTempFileForDownload(App.get(), originalFilename)
                        tempFile = newTempFile
                        continue
                    } else {
                        listener.onError(downloadId, e)
                        throw if (retryCount >= config.maxRetries) RetryException(e.toString()) else e
                    }
                }
            }
            listener.onError(downloadId, RetryException("Max retries exceeded"))
            throw RetryException("Max retries exceeded")
        }
    }

    private fun generateDownloadId(url: String): String {
        return "download_${UUID.nameUUIDFromBytes(url.toByteArray()).toString()}"
    }

    private fun finalizeDownload(tempFile: File, targetFile: File): Boolean {
        // 如果目标文件已存在，删除它以避免冲突
        if (targetFile.exists()) {
            targetFile.delete()
        }

        // 尝试将临时文件重命名为目标文件
        val success = tempFile.renameTo(targetFile)
        if (!success) {
            // 如果重命名失败，尝试复制文件内容
            tempFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.delete() // 删除临时文件
            println("⚠️ 重命名失败，已通过复制完成文件保存")
        } else {
            println("✅ 下载完成，文件已保存为: ${targetFile.absolutePath}")
        }

        return true
    }




    private fun verifyFilenameConsistency(
        original: String,
        response: Response
    ): String {
        val newName = getOriginalFilename(response)
        if (newName != original) {
            throw IOException("Filename changed during download: $original -> $newName")
        }
        return newName
    }

    private fun handleResume(tempFile: File, enableResume: Boolean): Long {
        return when {
            enableResume && tempFile.exists() -> {
                tempFile.length().also {
                    check(it < config.maxFileSize) { "File size exceeds limit" }
                }
            }
            else -> {
                tempFile.delete()
                0L
            }
        }
    }

    private suspend fun fetchResponse(
        url: String,
        downloadedBytes: Long
    ): Response {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$downloadedBytes-")
            .header("Cache-Control", "no-cache")
            .apply {
                if (config.enableChecksum) {
                    header("X-Require-Checksum", "sha1")
                }
            }
            .build()

        return withTimeout(config.networkTimeout) {
            HttpClient.provide().newCall(request).await()
        }
    }

    private suspend fun okhttp3.Call.await(): Response {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : okhttp3.Callback {
                override fun onResponse(call: okhttp3.Call, response: Response) {
                    // 不需要显式切换线程，OkHttp 回调本身在后台线程
                    continuation.resume(response) {
                        response.close() // 关闭资源
                    }
                }

                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            }
            // 正确取消处理
            continuation.invokeOnCancellation {
                // 关键修改：移除 withContext，直接调用 cancel()
                try {
                    cancel() // OkHttp 的 cancel() 是线程安全的
                } catch (e: Exception) {
                    Log.e("Network", "取消请求失败", e)
                }
            }
            enqueue(callback) // 发起异步请求
        }
    }


    private suspend fun processResponse(
        response: Response,
        tempFile: File,
        savePath: String,
        listener: DownloadListener,
        offset: Long,
        downloadId: String
    ) {
        when {
            response.isSuccessful -> handleSuccess(response, tempFile, savePath, listener, offset, downloadId)
            response.code == 416 -> handleRangeError(tempFile)
            else -> throw HttpException(response.code, response.message)
        }
    }

    private suspend fun handleSuccess(
        response: Response,
        tempFile: File,
        savePath: String,
        listener: DownloadListener,
        offset: Long,
        downloadId: String
    ) {
        response.body?.use { body ->
            FileUtils.saveWithChecksum(body, tempFile, offset, listener, config, downloadId)
        } ?: throw IOException("Empty response body")
//        response.body?.use { body ->
//            FileUtils.saveWithChecksum(
//                body = body,
//                file = tempFile,
//                offset = offset,
//                listener = listener,
//                config = config
//            )
//            try {
//                FileUtils.atomicMove(tempFile, File(savePath))
//            } catch (e: Exception) {
//                tempFile.delete()
//                throw e
//            }
//
//            withContext(config.listenerContext) {
//                listener.onSuccess(File(savePath))
//            }
//        } ?: throw IOException("Empty response body")
    }

    private fun handleRangeError(tempFile: File) {
        tempFile.delete()
        throw IOException("Requested range not satisfiable")
    }

    private suspend fun handleRetry(
        e: Exception,
        retryCount: Int,
        tempFile: File,
        savePath: String,
        listener: DownloadListener,
        downloadId: String // 新增 downloadId 参数
    ): Boolean {
        return when {
            isRetryable(e) && retryCount < config.maxRetries -> {
                delay(config.calculateBackoff(retryCount))
                true
            }
            else -> {
                withContext(config.listenerContext) {
                    listener.onError(downloadId, translateException(e)) // 传递 downloadId
                }
                false
            }
        }
    }


    private fun isRetryable(e: Exception): Boolean = when (e) {
        is SocketTimeoutException,
        is ConnectException,
        is SSLHandshakeException -> true
        is HttpException -> e.statusCode in 500..599
        else -> false
    }

    private fun translateException(e: Exception): Throwable = when (e) {
        is SecurityException -> e.apply { printStackTrace() }
        is HttpException -> IOException("Network error: ${e.message}", e).apply { printStackTrace() }
        else -> {
            // 添加详细日志
            Log.e("FileDownloader", "文件操作异常", e)
            IOException("文件IO错误: ${e.javaClass.simpleName}", e) // 显示具体异常类型
        }
    }

}




