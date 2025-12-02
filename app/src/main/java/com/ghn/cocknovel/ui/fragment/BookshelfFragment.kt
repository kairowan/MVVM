package com.ghn.cocknovel.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.basemodel.base.basefra.BaseFragment
import com.ghn.cocknovel.BR
import com.ghn.cocknovel.R
import com.ghn.cocknovel.databinding.FragmentBookshelfBinding
import com.ghn.cocknovel.viewmodel.RecommendViewModel


class BookshelfFragment() : BaseFragment<FragmentBookshelfBinding, RecommendViewModel>() {
//    var adapter: BaseRecyclerAdapter<Datas>? = null
    override fun initVariableId(): Int {
        return BR.mode
    }

    override fun initContentView(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentBookshelfBinding = FragmentBookshelfBinding.inflate(inflater,container,false)


    override fun initParam() {
    }

    override fun initView() {

    }

    override fun initData() {

    }

    override fun initViewObservable() {
        mViewModel.getBanner()
//        val RecommList =   mutableListOf<datas>()
//        viewModel?.getwan()
//        RecommRecyclerview.layoutManager = LinearLayoutManager(activity)
//        adapter = object : BaseRecyclerAdapter<datas>(RecommList) {
//            override fun bindData(holder: BaseViewHolder?, position: Int) {
//                val text: TextView = holder?.getView(R.id.recomm_title) as TextView
//                text.text = datas[position].title
//            }
//
//            override val layoutId: Int = R.layout.animation
//        }
//        val controller =
//            LayoutAnimationController(AnimationUtils.loadAnimation(activity, R.anim.animate))
//        RecommRecyclerview.layoutAnimation = controller
//        RecommRecyclerview.adapter = adapter
//        viewModel?.loginStatus?.observe(this) {
//            adapter?.addData(it.datas)
//        }
    }
}