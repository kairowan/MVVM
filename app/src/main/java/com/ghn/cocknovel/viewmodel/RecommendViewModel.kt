package com.ghn.cocknovel.viewmodel

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.MutableLiveData
import com.example.basemodel.base.basevm.BaseViewModel
import com.ghn.cocknovel.net.DataService
import com.ghn.cocknovel.ui.activity.WebviewActivity
import com.google.gson.Gson
import com.kt.NetworkModel.App
import com.kt.NetworkModel.bean.ProjectBean
import com.kt.NetworkModel.bean.TabFrameBean
import com.kt.network.bean.FontDataNew
import com.stx.xhb.androidx.entity.BaseBannerInfo

/**
 * @author 浩楠
 *
 * @date 2023/4/6-17:16.
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO
 */
open class RecommendViewModel(application: Application) : BaseViewModel(application) {
    companion object {
        val TAG: String? = BookStoreViewModel::class.simpleName


    }

    val homeStatus = MutableLiveData<FontDataNew>()
    val mBanner = MutableLiveData<List<BaseBannerInfo>>()
    val mProject = MutableLiveData<MutableList<ProjectBean.Data>>()
    val mProjectcontent = MutableLiveData<TabFrameBean.Data>()
    open fun getBanner() {
//        launchOnlyresult({
//            DataService.wbanner(5)
//        }, {
//            Log.i(TAG, "getBanner: $it")
////            mBanner.value = it as List<BaseBannerInfo>
//        },{
//            Log.i(TAG, "getBanner: ${it.code}")
//        },{
//            Log.i(TAG, "getBanner: $it")
//        })
        launchGo({
            DataService.wbanner(5).also {
                mBanner.value=it.data as List<BaseBannerInfo>
                Log.i(TAG, "getBanner: ${Gson().toJson(it)}")
                Log.i(TAG, "getBanner: ${it.errorCode}")
            }
        })
    }

    /**
     * 通过flow进行网络请求
     */
    open fun  flowbanner(){
        launchFlow({
            DataService.wbanner(5)
        },{
            Log.i(TAG, "flowbanner: $it")
        },true)
    }

    open fun getHomeStatus(page:Int) {
        launchOnlyresult({
            DataService.callback(5,page)
        }, {
//            homeStatus.addAll(it!!.datas)
            homeStatus.value=it
        },{},{},false)
    }


    open fun setWebview(url: String) {
        val bundle = Bundle()
        bundle.putString("url", url)
        startActivity(WebviewActivity::class.java, bundle, 1000)
    }


    open fun getProject() {
        launchOnlyresult({
            DataService.project(5)
        }, {
//            mProject.value = it
        })
    }

    open fun project_content(page: Int, cid: Int) {
        launchOnlyresult({
            DataService.project_content(5, page, cid)
        }, {
            Log.i(TAG, "project_content: ${it}")
//            mProjectcontent.value = it
        })
    }


//    open fun getwan() {
//        launchOnlyresult({
//            DataService.callback(5)
//        }, {
//            loginStatus.value = it
//        })
//    }

    open fun dow() {

    }


}