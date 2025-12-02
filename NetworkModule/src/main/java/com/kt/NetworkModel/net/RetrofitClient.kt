package com.kt.network.net

import android.annotation.SuppressLint
import android.content.Context
import com.kt.NetworkModel.net.interceptor.Level
import com.kt.NetworkModel.net.interceptor.LoggingInterceptor
import com.kt.ktmvvm.lib.BuildConfig
import com.kt.ktmvvm.net.event.OkHttpEventListener
import com.kt.network.net.dns.OkHttpDNS
import com.kt.network.net.interceptor.HTTPDNSInterceptor
import com.kt.network.net.interceptor.NoNetworkInterceptor
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.internal.platform.Platform
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitClient
/**
 * retrofit 初始化build
 */(var context: Context?) {


    companion object {

        @SuppressLint("StaticFieldLeak")
        private var retrofitClient: RetrofitClient? = null
        private const val DEFAULT_TIME_OUT = 15
        private val sRetrofitManager: MutableMap<Int, Retrofit> = HashMap()
        fun getInstance(context: Context?): RetrofitClient {
            if (retrofitClient == null) {
                synchronized(RetrofitClient::class.java) {
                    retrofitClient = RetrofitClient(context)
                    return retrofitClient as RetrofitClient
                }
            }
            return retrofitClient as RetrofitClient
        }

    }


    /**
     * 创建连接客户端
     */
    private fun createOkHttpClient(optimization: Boolean, update: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
        var captureInterceptor: Interceptor? = null
        try {
            val clazz =
                Class.forName("cn.coderpig.cp_network_capture.interceptor.CaptureInterceptor")
            val constructor = clazz.getDeclaredConstructor()
            captureInterceptor = constructor.newInstance() as Interceptor
        } catch (e: Exception) {
            e.printStackTrace()
        }
        builder.connectTimeout(DEFAULT_TIME_OUT.toLong(), TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIME_OUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIME_OUT.toLong(), TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(8, 10, TimeUnit.SECONDS)) //添加这两行代码
            .sslSocketFactory(TrustAllCerts.createSSLSocketFactory()!!, TrustAllCerts())
            .hostnameVerifier(TrustAllCerts.TrustAllHostnameVerifier())
            //alibaba dns优化
            .addInterceptor(captureInterceptor!!)
            .dns(OkHttpDNS.get(context))
            .eventListenerFactory(OkHttpEventListener.FACTORY)
        if (optimization) {
            builder.addInterceptor(HTTPDNSInterceptor(context)) //不建议用这种方式，因为大型APP 域名会比较多，假设HTTPS 的话，证书会认证失败
                .cache(context?.cacheDir?.let { Cache(it, 50 * 1024 * 1024L) })//缓存目录
                .addInterceptor(NoNetworkInterceptor(context))//无网拦截器
        }
        if (update==false) {
            builder.addNetworkInterceptor(LoggingInterceptor().apply {
                isDebug = BuildConfig.DEBUG
                level = Level.BASIC
                type = Platform.INFO
                requestTag = "Request"
                requestTag = "Response"
            })
        }
        return builder.build()
    }


    /**
     * 根据host 类型判断是否需要重新创建Client，因为一个app 有不同的BaseUrl，切换BaseUrl 就需要重新创建Client
     * 所以，就根据类型来从map中取出对应的client
     */
    fun <T> getDefault(interfaceServer: Class<T>?, hostType: Int): T {
        val retrofitManager = sRetrofitManager[hostType]
        return if (retrofitManager == null) {
            create(interfaceServer, hostType)
        } else retrofitManager.create(interfaceServer!!)
    }

    /**
     *
     */
    private fun <T> create(interfaceServer: Class<T>?, hostType: Int, update: Boolean = false): T {

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BaseUrlConstants.getHost(hostType))
            .addConverterFactory(GsonConverterFactory.create())
//            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(createOkHttpClient(true, update))
            .build()
        sRetrofitManager[hostType] = retrofit
        if (interfaceServer == null) {
            throw RuntimeException("The Api InterfaceServer is null!")
        }
        return retrofit.create(interfaceServer)
    }

}