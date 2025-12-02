package com.kt.NetworkModel.download.exceptions

import okio.IOException

/**
 * @author 浩楠
 *
 * @date 2025/4/4-15:39
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO
 */
// 修改后的异常类
class RetryException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class HttpException(val statusCode: Int, message: String) : IOException("HTTP $statusCode: $message")