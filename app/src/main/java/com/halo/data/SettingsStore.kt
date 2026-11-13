package com.halo.data

import android.content.Context
import com.halo.domain.HaloSettings

/** 꼼꼼히 안내 같은 설정과, 하루 첫 브리핑·올겨울 첫 영하 안내를 했는지 기억한다 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("halo_settings", Context.MODE_PRIVATE)

    fun load(): HaloSettings = HaloSettings(
        careful = prefs.getBoolean(KEY_CAREFUL, false),
        carefulOffered = prefs.getBoolean(KEY_CAREFUL_OFFERED, false),
        lastFullBriefingDay = prefs.getString(KEY_LAST_BRIEFING, null),
        firstFrostSeason = prefs.getString(KEY_FIRST_FROST, null),
    )

    fun save(settings: HaloSettings) {
        prefs.edit()
            .putBoolean(KEY_CAREFUL, settings.careful)
            .putBoolean(KEY_CAREFUL_OFFERED, settings.carefulOffered)
            .putString(KEY_LAST_BRIEFING, settings.lastFullBriefingDay)
            .putString(KEY_FIRST_FROST, settings.firstFrostSeason)
            .apply()
    }

    private companion object {
        const val KEY_CAREFUL = "careful"
        const val KEY_CAREFUL_OFFERED = "careful_offered"
        const val KEY_LAST_BRIEFING = "last_full_briefing_day"
        const val KEY_FIRST_FROST = "first_frost_season"
    }
}
