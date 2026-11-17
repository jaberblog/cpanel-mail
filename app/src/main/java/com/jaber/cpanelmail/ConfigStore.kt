package com.jaber.cpanelmail

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ConfigStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cpanel_config",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): Config? {
        val host = prefs.getString("host", null) ?: return null
        val user = prefs.getString("user", null) ?: return null
        val secret = prefs.getString("secret", null) ?: return null
        return Config(
            host = host,
            port = prefs.getInt("port", 2083),
            user = user,
            secret = secret,
            useToken = prefs.getBoolean("useToken", true),
            insecureSsl = prefs.getBoolean("insecure", false),
        )
    }

    fun save(c: Config) {
        prefs.edit()
            .putString("host", c.host)
            .putInt("port", c.port)
            .putString("user", c.user)
            .putString("secret", c.secret)
            .putBoolean("useToken", c.useToken)
            .putBoolean("insecure", c.insecureSsl)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
