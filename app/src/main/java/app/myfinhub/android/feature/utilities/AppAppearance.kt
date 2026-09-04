package app.myfinhub.android.feature.utilities

import android.content.Context

enum class AppAppearance(val storageValue: String, val label: String) {
    SYSTEM("system", "Σύστημα"),
    LIGHT("light", "Φωτεινό"),
    DARK("dark", "Σκούρο"),
    ;

    companion object {
        fun fromStorage(value: String?): AppAppearance = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object AppAppearancePreference {
    const val PREFERENCES_NAME = "myfinhub_local_preferences"
    const val KEY = "appearance"

    fun read(context: Context): AppAppearance = AppAppearance.fromStorage(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getString(KEY, null),
    )

    fun write(context: Context, appearance: AppAppearance) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, appearance.storageValue)
            .apply()
    }
}

/** Device-local privacy preference. It never enters the canonical finance document. */
object AmountVisibilityPreference {
    const val KEY = "amounts_visible"

    fun read(context: Context): Boolean = context
        .getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY, false)

    fun write(context: Context, visible: Boolean) {
        context.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, visible)
            .apply()
    }
}
