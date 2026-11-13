package com.halo.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.halo.domain.GleoCommand
import com.halo.domain.GleoParams
import com.halo.domain.GleoResponse
import com.halo.domain.GleoResult

/**
 * Gleo AI 가 보내는 기능 호출을 받는다 (schema.json 의 component = "service" → sendBroadcast, 패키지 지정).
 * 헤일로 화면이 떠 있으면 [GleoBridge] 로 넘겨 처리하고, 결과를 바로 Gleo 로 돌려준다.
 */
class GleoActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in GleoCommand.FUNCTIONS) return
        val command = GleoCommand.parse(action, readParams(intent))
        val result = when (command) {
            null -> GleoCommand.askingFor(action)
            else -> try {
                GleoBridge.handle(command) ?: GleoResult.fail("Open the Halo app first, then ask again.")
            } catch (t: Throwable) {
                // 처리 중 문제가 생겨도 앱이 꺼지지 않고 Gleo 에 실패로 답한다
                Log.w(TAG, "Gleo command failed", t)
                GleoResult.fail("Halo could not finish the request. Please try again.")
            }
        }
        reply(context, action, result)
    }

    /** ACTION_PARAM(JSON)과, 매개변수를 extra 하나씩 보낸 경우를 모두 읽는다 */
    private fun readParams(intent: Intent): Map<String, String?> {
        val params = mutableMapOf<String, String?>()
        params.putAll(GleoParams.parse(intent.getStringExtra(EXTRA_PARAM)))
        try {
            intent.extras?.let { extras ->
                for (key in extras.keySet()) {
                    if (key == EXTRA_PARAM || key in params) continue
                    @Suppress("DEPRECATION")
                    params[key] = extras.get(key)?.toString()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "extras read failed", t)
        }
        return params
    }

    private fun reply(context: Context, functionName: String, result: GleoResult) {
        try {
            context.sendBroadcast(
                Intent(GleoResponse.ACTION).apply {
                    putExtra("result", result.status.value)
                    putExtra("packageName", context.packageName)
                    putExtra("response", GleoResponse.json(functionName, result))
                },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Gleo response failed", t)
        }
    }

    private companion object {
        const val TAG = "GleoAction"
        const val EXTRA_PARAM = "ACTION_PARAM"
    }
}

/** 받은 기능 호출을 지금 떠 있는 헤일로 화면(HaloViewModel)으로 넘기는 통로. 화면이 없으면 null */
object GleoBridge {
    @Volatile
    var handler: ((GleoCommand) -> GleoResult)? = null

    fun handle(command: GleoCommand): GleoResult? = handler?.invoke(command)
}
