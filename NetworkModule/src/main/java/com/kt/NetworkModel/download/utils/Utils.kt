package com.kt.NetworkModel.download.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.URLUtil
import com.kt.NetworkModel.App
import com.kt.NetworkModel.download.listener.DownloadListener
import com.kt.NetworkModel.download.model.DownloadConfig
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.text.substring
import android.os.Environment
import com.kt.NetworkModel.download.utils.PathUtils.getDefaultDownloadDir


/**
 * @author 浩楠
 *
 * @date 2025/4/4-15:42
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO
 */
internal object FileUtils {
    fun createTempFileForDownload(context: Context,originalFileName: String): Pair<File, File> {
        // 获取私有目录中的 Download 子目录
        val privateDownloadDir = context.filesDir.resolve("Download").apply {
            mkdirs() // 确保目录存在
        }

        val tempFileName = "temp_${System.currentTimeMillis()}_${originalFileName}"
        val targetFileName = originalFileName // 目标文件名与原始文件名相同

        // 创建临时文件和目标文件
        val tempFile = File(privateDownloadDir, tempFileName).apply {
            if (exists()) delete() // 避免文件冲突
            createNewFile() // 确保文件存在
        }

        val targetFile = File(privateDownloadDir, targetFileName)

        return Pair(tempFile, targetFile) // 返回临时文件和目标文件
    }
    suspend fun saveWithChecksum(
        body: ResponseBody,
        file: File,
        offset: Long,
        listener: DownloadListener,
        config: DownloadConfig,
        downloadId: String
    ) {
        val totalSize = offset + body.contentLength()
        val buffer = ByteArray(config.bufferSize)
        var bytesRead: Int
        var bytesWritten = offset
        file.outputStream().buffered(config.bufferSize).use { output ->
            body.byteStream().use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    config.speedLimiter?.acquire(bytesRead)
                    output.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead
                    notifyProgress(listener, bytesWritten, totalSize, config, downloadId)
                }
            }
        }
    }


    @SuppressLint("NewApi") // 允许使用高版本 API
    fun atomicMove(source: File, target: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 在 Android 7.0+ 使用更可靠的方式
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } else {
            // 低版本回退方案
            if (!source.renameTo(target)) {
                throw IOException("Failed to move file: ${source.absolutePath}")
            }
        }

        // 统一设置权限
        if (!target.setReadable(true, false) || !target.setWritable(true, false)) {
            throw IOException("文件权限设置失败: ${target.absolutePath}")
        }
    }


    private suspend fun notifyProgress(
        listener: DownloadListener,
        bytesWritten: Long,
        totalSize: Long,
        config: DownloadConfig,
        downloadId: String
    ) {
        if (bytesWritten % config.progressInterval == 0L) {
            withContext(config.listenerContext) {
                listener.onProgress(
                    downloadId,
                    bytesWritten.toFloat() / totalSize,
                    bytesWritten,
                    totalSize
                )
            }
        }
    }

    fun getOriginalFilename(response: Response): String {
        // 优先级1: Content-Disposition 头解析
        val contentDisposition = response.header("Content-Disposition")?.let {
            parseContentDisposition(it)
        }
        // 优先级2: 从URL路径提取
        val urlFilename = response.request.url.pathSegments.lastOrNull()?.let {
            sanitizeFilename(it)
        }
        // 优先级3: 默认文件名（当无法获取时）
        return contentDisposition ?: urlFilename ?: generateDefaultFilename()
    }

    private fun parseContentDisposition(header: String): String? {
        val regex = Pattern.compile(
            "filename\\*?=((['\"])(.*?)\\2|([^;]+))",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = regex.matcher(header)
        if (!matcher.find()) return null
        // 处理 filename* 的编码（RFC 5987）
        val value = matcher.group(3) ?: matcher.group(4)
        return when {
            value?.startsWith("UTF-8''", ignoreCase = true) == true -> {
                URLDecoder.decode(value.substring(7), "UTF-8")
            }

            value != null -> {
                URLDecoder.decode(value, "UTF-8")
            }

            else -> null
        }?.let { sanitizeFilename(it) }
    }

    private fun sanitizeFilename(rawName: String): String {
        // 移除控制字符和Unicode问题字符
        val cleanName = rawName
            .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "") // 控制字符
            .replace(Regex("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]"), "_") // Unicode控制字符
            .replace(Regex("[/\\\\:*?\"<>|]"), "_") // 基础非法字符
            .replace(Regex("^[.\\s]+"), "") // 开头特殊字符
            .replace(Regex("[.\\s]+\$"), "") // 结尾特殊字符

        // 限制文件名长度（EXT4最大255字节）
        val maxLength = 255 - 32 // 预留扩展名空间
        val truncated = cleanName.take(maxLength)

        // 处理保留文件名（con、aux等）
        val reservedNames = setOf("CON", "PRN", "AUX", "NUL",
            "COM1", "LPT1", "LPT2", "LPT3")
        return if (reservedNames.contains(truncated.uppercase())) {
            "${truncated}_"
        } else {
            truncated
        }
    }


    private fun generateDefaultFilename(): String {
        // 生成时间戳文件名（示例：download_20230825_143056.dat）
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(Date())
        return "download_${timestamp}.dat"
    }
}

object PathUtils {
    fun getDefaultDownloadDir(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download").apply {
                if (!exists() && !mkdirs()) {
                    throw IOException("无法创建安全下载目录: $absolutePath")
                }
            }

    }
    fun normalizePath(context: Context, path: String): String {
        // 空路径直接返回默认目录
        if (path.isBlank()) return getDefaultDownloadDir(context).absolutePath

        val baseDir = getDefaultDownloadDir(context).canonicalFile
        val targetFile = when {
            path.startsWith(baseDir.absolutePath) -> File(path)
            File(path).isAbsolute -> throw SecurityException("禁止访问绝对路径: $path")
            else -> File(baseDir, path)
        }.canonicalFile

        // 增强校验逻辑
        if (!targetFile.absolutePath.startsWith(baseDir.absolutePath)) {
            throw SecurityException("路径越界: ${targetFile.path} → 基准目录: ${baseDir.path}")
        }

        return targetFile.absolutePath
    }





    internal object ValidationUtils {
        fun validateUrl(url: String, forceHttps: Boolean) {
            require(URLUtil.isValidUrl(url)) { "Invalid URL format" }
            if (forceHttps) {
                require(url.startsWith("https")) { "HTTPS required" }
            }
        }

        fun validatePath(path: String, allowedBase: String) {
            val normalized = PathUtils.normalizePath(App.get(), path)
            require(!normalized.contains("..")) { "Path traversal detected" }
            require(normalized.startsWith(allowedBase)) { "Invalid storage path" }
        }
    }

    internal object HttpClient {
        private val instance by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun provide(): OkHttpClient = instance
    }
}
