package com.halo.data.pleos;

import android.util.Log;

import java.util.List;

/**
 * Pleos 음성(TextToSpeech · SpeechToText)과 NaviHelper 리스너를 자바에서 만든다.
 * 인터페이스의 기본 구현(default)은 그대로 두고 쓰는 이벤트만 넘긴다.
 */
public final class PleosListeners {
    private static final String TAG = "PleosListeners";

    private PleosListeners() {
    }

    public interface SpeechSink {
        void onText(String text, boolean isFinal);

        void onFinished();
    }

    /** NaviHelper 값 (거리 m · 시간 초) */
    public interface NaviSink {
        void onDestination(String name, int remainDistance, int remainTime);

        void onArrived();

        void onRouteEnded();

        /** 내비가 알리는 주의 지점 (미끄럼 주의 · 안개 잦은 곳 등) */
        void onCautionPoint(String id, String type, int remainDistance);
    }

    // ---------------- TextToSpeech ----------------

    public static ai.pleos.playground.tts.listener.OnInitializeListener ttsInit(Runnable ok, Runnable fail) {
        return new ai.pleos.playground.tts.listener.OnInitializeListener() {
            @Override
            public void onSuccess() {
                ok.run();
            }

            @Override
            public void onFailure(ai.pleos.playground.tts.constant.ErrorType error) {
                Log.w(TAG, "TTS init failed: " + error);
                fail.run();
            }
        };
    }

    public static ai.pleos.playground.tts.listener.EventListener ttsEvents(Runnable finished, Runnable failed) {
        return new ai.pleos.playground.tts.listener.EventListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onReady() {
            }

            @Override
            public void onDone() {
                finished.run();
            }

            @Override
            public void onError(ai.pleos.playground.tts.constant.ErrorType error) {
                Log.w(TAG, "TTS error: " + error);
                failed.run();
            }

            @Override
            public void onStop() {
                finished.run();
            }

            @Override
            public void onUpdatedRms(double rms) {
            }
        };
    }

    // ---------------- SpeechToText ----------------

    public static ai.pleos.playground.stt.listener.OnInitializeListener sttInit(Runnable ok, Runnable fail) {
        return new ai.pleos.playground.stt.listener.OnInitializeListener() {
            @Override
            public void onSuccess() {
                ok.run();
            }

            @Override
            public void onFailure(ai.pleos.playground.stt.constant.ErrorType error) {
                Log.w(TAG, "STT init failed: " + error);
                fail.run();
            }
        };
    }

    public static ai.pleos.playground.stt.listener.ResultListener sttResults(SpeechSink sink) {
        return new ai.pleos.playground.stt.listener.ResultListener() {
            @Override
            public void onUpdated(String text, boolean isFinal) {
                sink.onText(text, isFinal);
            }

            @Override
            public void onUpdatedEpdData(long start, long end) {
            }

            @Override
            public void onStartedRecognition() {
            }

            @Override
            public void onEndedRecognition() {
                sink.onFinished();
            }

            @Override
            public void onError(ai.pleos.playground.stt.constant.ErrorType error) {
                Log.w(TAG, "STT error: " + error);
                sink.onFinished();
            }

            @Override
            public void onReady() {
            }

            @Override
            public void onUpdatedRms(double rms) {
            }
        };
    }

    // ---------------- NaviHelper ----------------

    public static ai.pleos.playground.navi.helper.listener.NaviHelperEventListener naviListener(NaviSink sink) {
        return new ai.pleos.playground.navi.helper.listener.NaviHelperEventListener() {
            @Override
            public void onDestinationInfo(ai.pleos.playground.navi.data.DestinationInfo info) {
                if (info == null) return;
                sink.onDestination(info.getName(), info.getRemainDistance(), info.getRemainTime());
            }

            @Override
            public void onDestinationArrived(ai.pleos.playground.navi.data.DestinationArrivedInfo info) {
                sink.onArrived();
            }

            @Override
            public void onRouteEnded() {
                sink.onRouteEnded();
            }

            @Override
            public void onRouteCancelled() {
                sink.onRouteEnded();
            }

            @Override
            public void onCautionPointInfo(List<ai.pleos.playground.navi.data.CautionPoint> list) {
                if (list == null) return;
                for (ai.pleos.playground.navi.data.CautionPoint point : list) {
                    if (point == null || point.getType() == null) continue;
                    sink.onCautionPoint(point.getId(), point.getType().name(), point.getRemainDistance());
                }
            }

            @Override
            public void onError(ai.pleos.playground.navi.constants.NaviErrorCode error) {
                Log.w(TAG, "navi error: " + error);
            }
        };
    }
}
