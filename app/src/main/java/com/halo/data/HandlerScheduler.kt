package com.halo.data

import android.os.Handler
import android.os.SystemClock
import com.halo.voice.Scheduler

/** 메인 스레드에서 나중에 할 일 */
class HandlerScheduler(private val handler: Handler) : Scheduler {
    override fun now(): Long = SystemClock.elapsedRealtime()

    override fun after(delayMs: Long, block: () -> Unit) {
        handler.postDelayed(block, delayMs)
    }
}
