package com.ghn.cocknovel.utils

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * @author 浩楠
 *
 * @date 2025/1/22   10:19
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ | |/ _` | ___ | __| | | |/ _` | |/ _ \
 *   / ___ | | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   __| |_|__,_|_|  ___/|_|__,_| |____/ __|__,_|__,_|_|___/
 * 描述: TODO 基于flow封装的点击事件
 */
object ViewFlowExtensions {

    /**
     * 收集多个 View 的事件流
     * @param events 可变参数，支持多种 View 事件（点击、长按、防抖点击）
     */
    fun LifecycleOwner.collectViewEvents(vararg events: ViewEvent) {
        lifecycleScope.launch {
            events.forEach { event ->
                launch {
                    when (event) {
                        is ViewEvent.Click -> event.view.clicks().collect { event.action() }
                        is ViewEvent.LongClick -> event.view.longClicks().collect { event.action() }
                        is ViewEvent.ThrottleClick -> event.view.throttleClicks(event.duration)
                            .collect { event.action() }
                        is ViewEvent.Touch -> event.view.touchActionsFlow().collect { event.touchHandler(it) }
                    }
                }
            }
        }
    }


    /**
     * 设置 View 的普通点击事件
     * @param action 点击时的操作
     */
    fun View.onClick(action: () -> Unit) {
        setOnClickListener { action() }
    }

    /**
     * 设置 View 的长按事件
     * @param action 长按时的操作
     */
    fun View.onLongClick(action: () -> Unit) {
        setOnLongClickListener {
            action()
            true
        }
    }

    /**
     * 创建一个 Flow 用于监听点击事件
     * @return Flow<Unit> 点击事件流
     */
    fun View.clicks(): Flow<Unit> = callbackFlow {
        val listener = View.OnClickListener { trySend(Unit).isSuccess }
        setOnClickListener(listener)
        awaitClose { setOnClickListener(null) }
    }.shareInLifecycle()

    /**
     * 创建一个 Flow 用于监听防抖点击事件
     * @param duration 防抖间隔时间（单位：毫秒），默认为 1000ms
     * @return Flow<Unit> 防抖点击事件流
     */
    fun View.throttleClicks(duration: Long = 1000L): Flow<Unit> =
        clicks().throttleFirst(duration)

    /**
     * 创建一个 Flow 用于监听长按事件
     * @return Flow<Unit> 长按事件流
     */
    fun View.longClicks(): Flow<Unit> = callbackFlow {
        val listener = View.OnLongClickListener {
            trySend(Unit).isSuccess
            true
        }
        setOnLongClickListener(listener)
        awaitClose { setOnLongClickListener(null) }
    }.shareInLifecycle()

    /**
     * 设置 View 的四方向点击事件（支持扩展触摸区域）
     * @param extraTouchArea 额外扩展的触摸区域大小
     * @param onStart 向左点击时触发的操作
     * @param onTop 向上点击时触发的操作
     * @param onEnd 向右点击时触发的操作
     * @param onBottom 向下点击时触发的操作
     */
    fun View.onDirectionalClick(
        extraTouchArea: Int = 0,
        onStart: (() -> Unit)? = null,
        onTop: (() -> Unit)? = null,
        onEnd: (() -> Unit)? = null,
        onBottom: (() -> Unit)? = null
    ) {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val viewBounds = calculateViewBounds(extraTouchArea)
                when {
                    onStart != null && event.x <= viewBounds.left -> {
                        onStart()
                        return@setOnTouchListener true
                    }

                    onTop != null && event.y <= viewBounds.top -> {
                        onTop()
                        return@setOnTouchListener true
                    }

                    onEnd != null && event.x >= viewBounds.right -> {
                        onEnd()
                        return@setOnTouchListener true
                    }

                    onBottom != null && event.y >= viewBounds.bottom -> {
                        onBottom()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    /**
     * 创建一个 Flow 用于监听四方向点击事件
     * @param extraTouchArea 额外扩展的触摸区域大小
     * @return Flow<DirectionalPosition> 四方向点击事件流
     */
    fun View.directionalClicks(extraTouchArea: Int = 0): Flow<DirectionalPosition> = callbackFlow {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val viewBounds = calculateViewBounds(extraTouchArea)
                when {
                    event.x <= viewBounds.left -> trySend(DirectionalPosition.START).isSuccess
                    event.y <= viewBounds.top -> trySend(DirectionalPosition.TOP).isSuccess
                    event.x >= viewBounds.right -> trySend(DirectionalPosition.END).isSuccess
                    event.y >= viewBounds.bottom -> trySend(DirectionalPosition.BOTTOM).isSuccess
                }
            }
            false
        }
        awaitClose { setOnTouchListener(null) }
    }.shareInLifecycle()



    /**
     * 创建一个 Flow，用于监听 View 的所有 Touch 事件
     * @return Flow<MotionEvent> 返回 MotionEvent 的流
     */
    fun View.touchActionsFlow(): Flow<MotionEvent> = callbackFlow {
        val listener = View.OnTouchListener { _, event ->
            trySend(event).isSuccess // 将 MotionEvent 发送到流中
            true // 消费事件
        }
        setOnTouchListener(listener) // 设置监听器
        awaitClose { setOnTouchListener(null) } // 在流关闭时移除监听器
    }.shareInLifecycle() // 绑定生命周期，防止内存泄漏


    /**
     * 创建一个 Flow，用于监听指定类型的 Touch 事件
     * @param actions 可变参数，指定监听的 MotionEvent 类型（如 ACTION_DOWN, ACTION_UP 等）
     * @return Flow<MotionEvent> 过滤后的 Touch 事件流
     */
    fun View.filteredTouchFlow(vararg actions: Int): Flow<MotionEvent> = touchActionsFlow()
        .filter { it.action in actions }

    /**
     * 创建一个 Flow，用于监听常用的 Touch 操作（如 ACTION_DOWN, ACTION_UP, ACTION_CANCEL）
     * @return Flow<TouchAction> 返回封装的 TouchAction 类型事件流
     */
    fun View.touchEventsFlow(): Flow<TouchAction> = callbackFlow {
        val listener = View.OnTouchListener { _, event ->
            val action = when (event.action) {
                MotionEvent.ACTION_DOWN -> TouchAction.Down
                MotionEvent.ACTION_UP -> TouchAction.Up
                MotionEvent.ACTION_CANCEL -> TouchAction.Cancel
                else -> null
            }
            action?.let { trySend(it).isSuccess }
            true // 消费事件
        }
        setOnTouchListener(listener)
        awaitClose { setOnTouchListener(null) }
    }.shareInLifecycle()


    /**
     * 遍历 ViewGroup 中的所有子 View 并执行操作
     * @param action 对每个子 View 执行的操作
     */
    private fun ViewGroup.forEachChild(action: (View) -> Unit) {
        for (i in 0 until childCount) {
            action(getChildAt(i))
        }
    }

    /**
     * 创建一个 ThrottleFirst 操作符
     * 用于过滤频繁的事件
     * @param duration 防抖间隔时间（单位：毫秒）
     * @return Flow<T> 限制频率后的事件流
     */
    private fun <T> Flow<T>.throttleFirst(duration: Long): Flow<T> = flow {
        var lastTime = 0L
        collect { value ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTime >= duration) {
                emit(value)
                lastTime = currentTime
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * 将 Flow 与生命周期绑定，避免内存泄漏
     * @return Flow<T> 生命周期绑定的事件流
     */
    private fun <T> Flow<T>.shareInLifecycle(): Flow<T> = shareIn(
        scope = CoroutineScope(Dispatchers.Main.immediate),
        started = SharingStarted.WhileSubscribed(5000L),
        replay = 0
    )

    /**
     * 计算扩展触摸区域的边界
     * @param extraTouchArea 额外扩展的触摸区域大小
     * @return Rect 包含扩展边界的矩形
     */
    private fun View.calculateViewBounds(extraTouchArea: Int): Rect {
        val rect = Rect()
        getHitRect(rect)
        rect.left -= extraTouchArea
        rect.top -= extraTouchArea
        rect.right += extraTouchArea
        rect.bottom += extraTouchArea
        return rect
    }

    /**
     * 四方向点击事件的枚举
     */
    enum class DirectionalPosition {
        START, TOP, END, BOTTOM
    }

    /**
     * 定义封装的 TouchAction 类型
     */
    sealed class TouchAction {
        object Down : TouchAction() // 按下
        object Up : TouchAction() // 松开
        object Cancel : TouchAction() // 取消
    }

    /**
     * 定义不同类型的 View 事件
     */
    sealed class ViewEvent {
        data class Click(val view: View, val action: () -> Unit) : ViewEvent() // 普通点击事件
        data class LongClick(val view: View, val action: () -> Unit) : ViewEvent() // 长按事件
        data class ThrottleClick(
            val view: View,
            val duration: Long = 1000L,
            val action: () -> Unit
        ) : ViewEvent() // 防抖点击事件，默认间隔为 1000ms
        data class Touch(val view: View, val touchHandler: (MotionEvent) -> Unit) : ViewEvent() // Touch 事件
    }
}

