package com.zxkws.voicetimer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zxkws.voicetimer.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionName: String, val apkUrl: String)

object UpdateManager {
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/zxkws/voice-timer-app/releases/latest"

    fun check(): UpdateInfo? {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val remoteVersion = json.getString("tag_name").removePrefix("v")
        if (compareVersion(remoteVersion, BuildConfig.VERSION_NAME) <= 0) return null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) return UpdateInfo(remoteVersion, asset.getString("browser_download_url"))
        }
        return null
    }

    fun download(context: Context, info: UpdateInfo): File {
        val dir = File(context.getExternalFilesDir(null), "Download").apply { mkdirs() }
        val target = File(dir, "voice-timer-${info.versionName}.apk")
        URL(info.apkUrl).openStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        return target
    }

    fun install(context: Context, apk: File): Boolean {
        if (SilentInstaller.tryRootInstall(apk)) return true
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return false
    }

    private fun compareVersion(a: String, b: String): Int {
        val av = a.split('.').map { it.toIntOrNull() ?: 0 }
        val bv = b.split('.').map { it.toIntOrNull() ?: 0 }
        repeat(maxOf(av.size, bv.size)) { i ->
            val x = av.getOrElse(i) { 0 }
            val y = bv.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
