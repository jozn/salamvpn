package app.firstvpn

import android.content.Context
import com.google.gson.Gson

class KKApp {
    companion object {
        private const val SHARED_PREF_NAME = "MySharedPref"

        fun saveToSharedPreferences(ctx: Context, key: String, value: String) {
            val sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString(key, value)
            editor.apply()
        }

        fun getFromSharedPreferences(ctx: Context, key: String, defaultValue: String = ""): String {
            val sharedPreferences = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
            return sharedPreferences.getString(key, defaultValue) ?: defaultValue
        }
    }
}

data class AppConfState(
    val lastConnectedProfileId: Long
)

fun saveAppConfState(context: Context, state: AppConfState) {
    val jsonState = Gson().toJson(state)
    KKApp.saveToSharedPreferences(context, "app_conf_state", jsonState)
}

fun loadAppConfState(context: Context): AppConfState? {
    val jsonState = KKApp.getFromSharedPreferences(context, "app_conf_state")
    return Gson().fromJson(jsonState, AppConfState::class.java)
}

fun updateLastConnectedProfileId(context: Context, id: Long) {
    if (id >= 0) saveAppConfState(context, AppConfState(lastConnectedProfileId = id))
}
