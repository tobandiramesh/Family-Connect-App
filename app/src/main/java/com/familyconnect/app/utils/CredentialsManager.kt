package com.familyconnect.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages persistent user credentials in SharedPreferences.
 * Used to restore service listeners even after app force-stop.
 */
object CredentialsManager {
    private const val TAG = "CredentialsManager"
    private const val PREFS_NAME = "family_connect_credentials"
    private const val KEY_USER_MOBILE = "user_mobile"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_LOGGED_IN = "is_logged_in"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save user credentials after successful login.
     * Called from FamilyViewModel.login() and auto-restore.
     */
    fun saveCredentials(context: Context, userMobile: String, userName: String) {
        try {
            val prefs = getPrefs(context)
            prefs.edit().apply {
                putString(KEY_USER_MOBILE, userMobile.trim())
                putString(KEY_USER_NAME, userName.trim())
                putBoolean(KEY_LOGGED_IN, true)
                apply()
            }
            Log.d(TAG, "💾 Credentials saved: $userMobile | $userName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving credentials: ${e.message}")
        }
    }

    /**
     * Get saved user mobile number.
     * Returns null if user is not logged in.
     */
    fun getUserMobile(context: Context): String? {
        return try {
            val prefs = getPrefs(context)
            val mobile = prefs.getString(KEY_USER_MOBILE, null)
            val isLoggedIn = prefs.getBoolean(KEY_LOGGED_IN, false)
            
            if (isLoggedIn && mobile?.isNotBlank() == true) {
                Log.d(TAG, "📱 Retrieved saved mobile: $mobile")
                mobile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting mobile: ${e.message}")
            null
        }
    }

    /**
     * Get saved user name.
     * Returns null if user is not logged in.
     */
    fun getUserName(context: Context): String? {
        return try {
            val prefs = getPrefs(context)
            val name = prefs.getString(KEY_USER_NAME, null)
            val isLoggedIn = prefs.getBoolean(KEY_LOGGED_IN, false)
            
            if (isLoggedIn && name?.isNotBlank() == true) {
                Log.d(TAG, "👤 Retrieved saved name: $name")
                name
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting name: ${e.message}")
            null
        }
    }

    /**
     * Check if user is currently logged in (has saved credentials).
     */
    fun isLoggedIn(context: Context): Boolean {
        return try {
            val prefs = getPrefs(context)
            prefs.getBoolean(KEY_LOGGED_IN, false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all saved credentials on logout.
     */
    fun clearCredentials(context: Context) {
        try {
            val prefs = getPrefs(context)
            prefs.edit().apply {
                remove(KEY_USER_MOBILE)
                remove(KEY_USER_NAME)
                putBoolean(KEY_LOGGED_IN, false)
                apply()
            }
            Log.d(TAG, "🗑️ Credentials cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing credentials: ${e.message}")
        }
    }
}
