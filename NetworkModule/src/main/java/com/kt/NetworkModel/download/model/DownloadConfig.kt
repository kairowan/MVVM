package com.kt.NetworkModel.download.model

import com.kt.NetworkModel.App
import com.kt.NetworkModel.download.listener.SpeedLimiter
import com.kt.NetworkModel.download.utils.PathUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.pow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * @author 浩楠
 *
 * @date 2025/4/4-15:35
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO
 */

data class DownloadConfig(
    val maxRetries: Int = 5,
    val bufferSize: Int = 16384,
    val networkTimeout: Duration = 30_000.milliseconds,
    val forceHttps: Boolean = true,
    val maxFileSize: Long = 1L shl 34,
    val progressInterval: Long = 100 * 1024,
    val allowedBasePath: File = PathUtils.getDefaultDownloadDir(App.get()),
    val listenerContext: CoroutineDispatcher = Dispatchers.Main,
    val speedLimiter: SpeedLimiter? = null,
    val enableChecksum: Boolean = true
) {
    companion object {
        var global: DownloadConfig = DownloadConfig()
    }

    internal fun calculateBackoff(retryCount: Int): Duration {
        return minOf(2.0.pow(retryCount).toLong() * 500, 10_000).milliseconds
    }
}


data class DownloadTaskInfo(
    val url: String,
    val fileName: String,
    var progress: Float = 0f
)