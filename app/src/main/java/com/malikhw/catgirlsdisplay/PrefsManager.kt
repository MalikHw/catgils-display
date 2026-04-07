package com.malikhw.catgirlsdisplay

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefsManager(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    var nsfwMode: String
        get() = prefs.getString("nsfw_mode", "no") ?: "no"
        set(value) = prefs.edit().putString("nsfw_mode", value).apply()
    
    var selectedTags: List<String>
        get() {
            val json = prefs.getString("tags", "[]") ?: "[]"
            return gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        }
        set(value) {
            val json = gson.toJson(value)
            prefs.edit().putString("tags", json).apply()
        }
    
    var autoNextInterval: Int
        get() = prefs.getInt("auto_next", 0)
        set(value) = prefs.edit().putInt("auto_next", value).apply()
    
    var starredImages: MutableList<SavedImage>
        get() {
            val json = prefs.getString("starred", "[]") ?: "[]"
            val type = object : TypeToken<MutableList<SavedImage>>() {}.type
            return gson.fromJson(json, type) ?: mutableListOf()
        }
        set(value) {
            prefs.edit().putString("starred", gson.toJson(value)).apply()
        }
    
    fun addStarred(img: SavedImage) {
        val current = starredImages
        current.add(img)
        starredImages = current
    }
    
    fun removeStarred(id: String) {
        val current = starredImages
        current.removeAll { it.id == id }
        starredImages = current
    }
    
    fun isStarred(id: String): Boolean {
        return starredImages.any { it.id == id }
    }
}
