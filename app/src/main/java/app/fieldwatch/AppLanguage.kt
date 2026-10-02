package app.fieldwatch

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class AppLanguage(val tag: String) {
    SYSTEM(""), ENGLISH("en"), SIMPLIFIED_CHINESE("zh-Hans");

    companion object {
        fun fromTag(tag: String): AppLanguage = when {
            tag.startsWith("zh", ignoreCase = true) -> SIMPLIFIED_CHINESE
            tag.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> SYSTEM
        }
    }
}

object AppLanguages {
    fun systemSelection(context: Context): AppLanguage = if (Build.VERSION.SDK_INT >= 33) {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        AppLanguage.fromTag(if (locales.isEmpty) "" else locales[0].toLanguageTag())
    } else AppLanguage.SYSTEM

    fun apply(context: Context, language: AppLanguage) {
        UiText.context = localizedContext(context, language)
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        listOf(
            Triple("fieldwatch_scan", R.string.notification_scan_channel, R.string.notification_scan_description),
            Triple("fieldwatch_watch_v3", R.string.notification_watch_channel, R.string.notification_watch_description),
        ).forEach { (id, name, description) ->
            notifications.getNotificationChannel(id)?.let { channel ->
                channel.name = UiText.text(name)
                channel.description = UiText.text(description)
                notifications.createNotificationChannel(channel)
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            val locales = LocaleList.forLanguageTags(language.tag)
            if (manager.applicationLocales != locales) manager.applicationLocales = locales
        }
    }

    fun localizedContext(context: Context, language: AppLanguage): Context {
        val config = Configuration(context.resources.configuration)
        val locale = if (language == AppLanguage.SYSTEM) {
            val system = if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).systemLocales
            } else Resources.getSystem().configuration.locales
            system[0] ?: Locale.ENGLISH
        } else Locale.forLanguageTag(language.tag)
        config.setLocale(if (locale.language == "zh") Locale.forLanguageTag("zh-Hans") else Locale.ENGLISH)
        return context.createConfigurationContext(config)
    }
}

/** Application-owned resource context; also used by services and non-composable UI helpers. */
object UiText {
    @Volatile
    lateinit var context: Context
        internal set

    fun explanation(source: String): String = UiExplanations.text(source)

    fun report(source: String): String {
        val reportText = UiReportText.text(source)
        if (reportText != source) return reportText
        val deviceText = UiDeviceReportText.text(source)
        return if (deviceText != source) deviceText else explanation(source)
    }

    fun text(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
}
