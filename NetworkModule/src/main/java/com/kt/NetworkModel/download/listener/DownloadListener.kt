package com.kt.NetworkModel.download.listener

import java.io.File

/**
 * @author 浩楠
 *
 * @date 2025/4/4-15:38
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO
 */
interface DownloadListener {
 fun onProgress(downloadId: String, progress: Float, downloadedBytes: Long, totalBytes: Long)
 fun onSuccess(downloadId: String, file: File)
 fun onError(downloadId: String, error: Throwable)
}
