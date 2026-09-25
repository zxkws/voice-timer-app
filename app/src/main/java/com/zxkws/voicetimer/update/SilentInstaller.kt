package com.zxkws.voicetimer.update

import java.io.File

object SilentInstaller {
    fun tryRootInstall(apk: File): Boolean = runCatching {
        val escaped = apk.absolutePath.replace("'", "'\\''")
        val process = ProcessBuilder("su", "-c", "pm install -r --user 0 '$escaped'").redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0
    }.getOrDefault(false)
}
