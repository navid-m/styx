package com.styx.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Helper object for Steam API operations
 */
object SteamApiHelper {
    data class SteamSearchResult(val appid: String, val name: String)

    fun searchGameByName(gameName: String): List<SteamSearchResult> {
        try {
            val encodedName = URLEncoder.encode(gameName, "UTF-8")
            val url = URL("https://steamcommunity.com/actions/SearchApps/$encodedName")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val listType = object : TypeToken<List<SteamSearchResult>>() {}.type
            val results: List<SteamSearchResult> = gson.fromJson(response, listType)

            return results.take(10)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}