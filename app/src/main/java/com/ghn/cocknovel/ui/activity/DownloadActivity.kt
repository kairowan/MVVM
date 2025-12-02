package com.ghn.cocknovel.ui.activity

import android.os.Bundle
import android.webkit.URLUtil
import android.widget.TextView
import com.kt.NetworkModel.download.utils.PathUtils
import androidx.lifecycle.lifecycleScope
import com.example.basemodel.base.BaseActivity
import com.example.basemodel.base.BaseViewModel
import com.ghn.cocknovel.BR
import com.ghn.cocknovel.R
import com.ghn.cocknovel.databinding.ActivityDownloadBinding
import com.kt.NetworkModel.download.core.FileDownloader
import com.kt.NetworkModel.download.exceptions.HttpException
import com.kt.NetworkModel.download.exceptions.RetryException
import com.kt.NetworkModel.download.listener.DownloadListener
import com.kt.NetworkModel.download.model.DownloadConfig
import com.kt.NetworkModel.download.model.DownloadTaskInfo
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class DownloadActivity : BaseActivity<ActivityDownloadBinding, BaseViewModel>() {
    val downloadTasks = mutableMapOf<String, DownloadTaskInfo>()
    var downloader: FileDownloader? = null
    val urls = listOf(
        "https://raw.githubusercontent.com/WVector/AppUpdateDemo/master/apk/sample-debug.apk",
        "https://raw.githubusercontent.com/WVector/AppUpdateDemo/master/json/json1.txt",
        "https://raw.githubusercontent.com/WVector/AppUpdateDemo/master/json/json.txt"
    )

    override fun initVariableId(): Int = BR.mode
    override fun initContentView(savedInstanceState: Bundle?): Int = R.layout.activity_download
    override fun initParam() {
        mBinding.TvDownload.setOnClickListener {
            // 1. 创建配置（可选自定义）
            val customConfig = DownloadConfig(
                maxRetries = 5,
                bufferSize = 16384,
                networkTimeout = 60.seconds,
                progressInterval = 1024 * 1024 // 每1MB更新进度
            )
            // 2. 初始化下载器
            downloader = FileDownloader.create(customConfig)
            // 3. 实现监听器
            val listener = object : DownloadListener {
                override fun onProgress(
                    downloadId: String,
                    progress: Float,
                    downloadedBytes: Long,
                    totalBytes: Long
                ) {
                    // 根据 downloadId 获取任务信息
                    val taskInfo = downloadTasks[downloadId] ?: return
                    // 更新任务进度
                    taskInfo.progress = progress
                    // 打印进度信息
                    println("[$downloadId - ${taskInfo.fileName}] 下载进度: ${"%.2f".format(progress * 100)}%")
                    // 渲染到 UI
                    mBinding.TvPercent.text =
                        "[$downloadId - ${taskInfo.fileName}] 下载进度: ${"%.2f".format(progress * 100)}%"
                }

                override fun onSuccess(downloadId: String, file: File) {
                    // 根据 downloadId 获取任务信息
                    val taskInfo = downloadTasks[downloadId] ?: return
                    // 打印成功信息
                    println("[$downloadId - ${taskInfo.fileName}] 文件名：${file.name}")
                    println("[$downloadId - ${taskInfo.fileName}] 文件大小：${file.length()} bytes")
                    println("[$downloadId - ${taskInfo.fileName}] 下载完成！文件路径：${file.absolutePath}")
                    // 清除已完成的任务
                    downloadTasks.remove(downloadId)
                }

                override fun onError(downloadId: String, error: Throwable) {
                    // 根据 downloadId 获取任务信息
                    val taskInfo = downloadTasks[downloadId] ?: return
                    // 打印错误信息
                    when (error) {
                        is RetryException -> println("[$downloadId - ${taskInfo.fileName}] 重试失败: ${error.cause?.message}")
                        else -> println("[$downloadId - ${taskInfo.fileName}] 下载错误: ${error.message}")
                    }
                    // 清除失败的任务
                    downloadTasks.remove(downloadId)
                }
            }
            lifecycleScope.launch {
                try {
                    downloader?.execute(
                        url = "https://dldir1.qq.com/weixin/android/weixin8015android2020_arm64.apk",
                        savePath = "${PathUtils.getDefaultDownloadDir(this@DownloadActivity)}/app.apk",
                        listener = listener,
                        enableResume = true // 启用断点续传
                    )
                } catch (e: Exception) {
                    when (e) {
                        is SecurityException -> println("权限错误: ${e.message}")
                        is HttpException -> println("HTTP错误: ${e.statusCode}")
                        else -> println("未知错误: ${e.message}")
                    }
                }
            }
        }
        mBinding.TvDownloads.setOnClickListener {
            val savePathPrefix = PathUtils.getDefaultDownloadDir(this@DownloadActivity)
            startDownloads(
                urls = urls,
                savePathPrefix = savePathPrefix.toString(),
                listener = object : DownloadListener {
                    override fun onProgress(
                        downloadId: String,
                        progress: Float,
                        downloadedBytes: Long,
                        totalBytes: Long
                    ) {
                        // 获取当前任务对应的文件名
                        val taskInfo = downloadTasks[downloadId]
                        val fileName = taskInfo?.fileName ?: "Unknown"
                        // 打印进度和路径
                        println("[$downloadId - $fileName] 下载进度: ${"%.2f".format(progress * 100)}%")
                        // 更新 TvPercents 控件
                        updateTvPercents(downloadId, fileName, progress)
                    }

                    override fun onSuccess(downloadId: String, file: File) {
                        // 获取当前任务对应的文件名
                        val taskInfo = downloadTasks[downloadId]
                        val fileName = taskInfo?.fileName ?: "Unknown"
                        // 打印成功信息
                        println("[$downloadId - $fileName] 文件下载成功！路径：${file.absolutePath}")
                        // 更新 TvPercents 控件（标记为完成）
                        updateTvPercents(downloadId, fileName, 1f)
                    }

                    override fun onError(downloadId: String, error: Throwable) {
                        // 获取当前任务对应的文件名
                        val taskInfo = downloadTasks[downloadId]
                        val fileName = taskInfo?.fileName ?: "Unknown"
                        // 打印错误信息
                        println("[$downloadId - $fileName] 下载失败: ${error.message}")
                        // 更新 TvPercents 控件（标记为失败）
                        updateTvPercents(downloadId, fileName, -1f)
                    }
                }
            )
        }
    }

    private fun updateTvPercents(downloadId: String, fileName: String, progress: Float) {
        // 构建显示文本
        val progressText = when {
            progress < 0 -> "$fileName: 下载失败"
            progress == 1f -> "$fileName: 下载完成"
            else -> "$fileName: ${"%.2f".format(progress * 100)}%"
        }
        // 在主线程更新 UI
        runOnUiThread {
            val tvPercents = findViewById<TextView>(R.id.TvPercents)
            tvPercents.text = "${tvPercents.text}\n$progressText"
        }
    }

    fun startDownloads(urls: List<String>, savePathPrefix: String, listener: DownloadListener) {
        lifecycleScope.launch {
            urls.forEach { url ->
                try {
                    val downloadId = generateDownloadId(url)
                    val fileName = URLUtil.guessFileName(url, null, null)
                    val savePath = "$savePathPrefix/$fileName"

                    println("开始下载: [$downloadId - $fileName]")
                    println("保存路径: $savePath")

                    downloadTasks[downloadId] = DownloadTaskInfo(url, fileName)

                    downloader?.execute(
                        url = url,
                        savePath = savePath,
                        listener = object : DownloadListener {
                            override fun onProgress(
                                downloadId: String,
                                progress: Float,
                                downloadedBytes: Long,
                                totalBytes: Long
                            ) {
                                listener.onProgress(downloadId, progress, downloadedBytes, totalBytes)
                                println("[$downloadId - $fileName] 下载进度: ${"%.2f".format(progress * 100)}%")
                            }

                            override fun onSuccess(downloadId: String, file: File) {
                                listener.onSuccess(downloadId, file)
                                println("[$downloadId - $fileName] 文件下载成功！路径：${file.absolutePath}")
                            }

                            override fun onError(downloadId: String, error: Throwable) {
                                listener.onError(downloadId, error)
                                println("[$downloadId - $fileName] 下载失败: ${error.message}")
                            }
                        },
                        enableResume = true
                    )
                } catch (e: Exception) {
                    println("[$url] 下载任务异常: ${e.message}")
                }
            }
        }
    }


    private fun generateDownloadId(url: String): String {
        return "download_${UUID.nameUUIDFromBytes(url.toByteArray()).toString()}"
    }
}