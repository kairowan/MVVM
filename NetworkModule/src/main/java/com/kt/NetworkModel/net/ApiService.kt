package com.kt.network.net

import com.kt.NetworkModel.bean.ProjectBean
import com.kt.NetworkModel.bean.TabFrameBean
import com.kt.NetworkModel.bean.WBanner
import com.kt.network.bean.BaseResult
import com.kt.network.bean.FontDataNew
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ApiService {
    /**
     * 登录接口
     */
    @GET(ApiAddress.LOGIN)
    suspend fun Login(
        @Query("phoneNumber") phoneNumber: String
    ):  BaseResult<Any>

    /**
     * 首页文章
     */
    @GET("article/list/{page}/json")
    suspend fun callback(@Query("page")page: Int): BaseResult<FontDataNew>

    /**
     * 轮播图
     */
    @GET(ApiAddress.BANNER)
    suspend fun banner(): BaseResult<MutableList<WBanner.Data>>

    /**
     * 项目分类
     */
    @GET(ApiAddress.PROJECT)
    suspend fun project(): BaseResult<MutableList<ProjectBean.Data>>

    /**
     * 项目列表数据
     */
    @GET("project/list/{page}/json?")
    suspend fun project_content(
        @Path("page") page: Int,
        @Query("cid") cid: Int
    ): BaseResult<TabFrameBean.Data>

    /**
     * 下载接口
     */
    @Streaming
    @GET
    suspend fun downloadFile(
        @Url url: String,
        @Header("Range") range: String // 参数类型必须为String
    ): Response




//    /**
//     * get
//     */
//    @GET(ApiAddress.LOGIN)
//    suspend fun login(
//        @Query("account") account: String,
//        @Query("password") password: String
//    ): BaseResponse<Any>
//
//
//    /**
//     * post body
//     */
//    @POST(ApiAddress.LOGIN)
//    suspend fun loginBody(@Body requestBody: RequestBody): BaseResponse<Any>
} 