package app.firstvpn

import android.content.Context

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
