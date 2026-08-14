package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.model.BackupInspection
import com.msa.iotofflinetoolbox.core.policy.HttpRequestValidationIssue

/** Application message policy kept outside operation orchestration. */
internal class ToolboxMessages(
    private val language: () -> AppLanguage,
) {
    fun text(english: String, persian: String): String =
        if (language() == AppLanguage.PERSIAN) persian else english

    fun source(source: String): String {
        if (language() != AppLanguage.PERSIAN) return source
        if (source.endsWith(" security", ignoreCase = true)) {
            return source(source.dropLast(" security".length)) + " - امنیت"
        }
        return when (source) {
            "Navigation settings" -> "تنظیمات ناوبری"
            "Subnet" -> "زیرشبکه"
            "Ping" -> "پینگ"
            "DNS" -> "DNS"
            "TCP" -> "TCP"
            "UDP" -> "UDP"
            "Port scanner" -> "اسکن پورت"
            "LAN discovery" -> "کشف شبکه محلی"
            "Discovery" -> "کشف دستگاه"
            "SSDP" -> "SSDP"
            "mDNS" -> "mDNS"
            "HTTP" -> "HTTP"
            "WebSocket" -> "WebSocket"
            "MQTT", "MQTT WebSocket" -> "MQTT"
            "CoAP" -> "CoAP"
            "Payload" -> "Payload"
            "Devices" -> "دستگاه‌ها"
            "Device inventory" -> "فهرست دستگاه‌ها"
            "Profiles" -> "پروفایل‌ها"
            "Endpoint profiles" -> "پروفایل‌های Endpoint"
            "Templates" -> "قالب‌ها"
            "Payload templates" -> "قالب‌های Payload"
            "Backup" -> "نسخه پشتیبان"
            "Backup export" -> "خروجی نسخه پشتیبان"
            "Backup import" -> "ورود نسخه پشتیبان"
            "Application settings" -> "تنظیمات برنامه"
            "Settings" -> "تنظیمات"
            "Activity logs" -> "لاگ‌های فعالیت"
            "Request history" -> "تاریخچه درخواست‌ها"
            "History" -> "تاریخچه"
            "System" -> "سیستم"
            "Storage recovery" -> "بازیابی ذخیره‌سازی"
            "Storage" -> "ذخیره‌سازی"
            else -> source
        }
    }

    fun httpValidationIssue(issue: HttpRequestValidationIssue): String = when (issue) {
        HttpRequestValidationIssue.INVALID_URL -> text("HTTP URL is invalid or contains embedded credentials", "نشانی HTTP نامعتبر است یا اطلاعات ورود را داخل URL قرار داده است")
        HttpRequestValidationIssue.PUBLIC_CLEARTEXT_BLOCKED -> text("Public cleartext HTTP is blocked by default", "HTTP رمزنگاری‌نشده عمومی به‌صورت پیش‌فرض مسدود است")
        HttpRequestValidationIssue.UNSUPPORTED_METHOD -> text("HTTP method is not supported", "متد HTTP پشتیبانی نمی‌شود")
        HttpRequestValidationIssue.INVALID_TIMEOUT -> text("HTTP timeout values must be between 100 and 120000 ms", "مقادیر Timeout باید بین ۱۰۰ تا ۱۲۰۰۰۰ میلی‌ثانیه باشند")
        HttpRequestValidationIssue.REDIRECT_UNSUPPORTED -> text("Manual redirect inspection is unavailable on this platform", "بررسی دستی Redirect در این پلتفرم در دسترس نیست")
        HttpRequestValidationIssue.INVALID_HEADERS -> text("HTTP header block is invalid", "ساختار Headerهای HTTP نامعتبر است")
        HttpRequestValidationIssue.RESTRICTED_HEADERS -> text("One or more HTTP headers are managed by the engine", "یک یا چند Header توسط موتور HTTP مدیریت می‌شوند")
        HttpRequestValidationIssue.BROWSER_FORBIDDEN_HEADERS -> text("One or more HTTP headers are controlled by the browser", "یک یا چند Header توسط مرورگر کنترل می‌شوند")
        HttpRequestValidationIssue.COOKIE_UNSUPPORTED -> text("This platform cannot set the Cookie header manually", "این پلتفرم امکان تنظیم دستی Header مربوط به Cookie را ندارد")
        HttpRequestValidationIssue.DUPLICATE_COOKIE -> text("Use either the Cookie editor or a manual Cookie header, not both", "فقط یکی از ویرایشگر Cookie یا Header دستی Cookie را استفاده کنید")
        HttpRequestValidationIssue.DUPLICATE_CONTENT_TYPE -> text("Use the Content Type field instead of a manual Content-Type header", "به‌جای Header دستی Content-Type از فیلد نوع محتوا استفاده کنید")
        HttpRequestValidationIssue.INVALID_QUERY -> text("HTTP query parameters are invalid", "پارامترهای Query نامعتبر هستند")
        HttpRequestValidationIssue.INVALID_COOKIES -> text("HTTP cookie entries are invalid", "مقادیر Cookie نامعتبر هستند")
        HttpRequestValidationIssue.INVALID_AUTH -> text("HTTP authentication settings are invalid", "تنظیمات احراز هویت HTTP نامعتبر هستند")
        HttpRequestValidationIssue.INVALID_BODY -> text("HTTP request body or content type is invalid", "بدنه درخواست یا نوع محتوای HTTP نامعتبر است")
        HttpRequestValidationIssue.REQUEST_BODY_TOO_LARGE -> text("HTTP request body exceeds the 1 MiB safety limit", "بدنه درخواست از سقف ایمنی ۱ مگابایت عبور کرده است")
    }

    fun localizeBackupInspection(raw: String, inspection: BackupInspection): BackupInspection {
        val message = when {
            inspection.compatible -> text(
                "Compatible backup. Choose Merge to preserve local records or Replace to overwrite them.",
                "نسخه پشتیبان سازگار است. برای حفظ داده‌های محلی «ادغام» و برای جایگزینی کامل «جایگزینی» را انتخاب کنید.",
            )
            raw.isBlank() -> text("Backup JSON cannot be empty", "متن JSON نسخه پشتیبان نمی‌تواند خالی باشد")
            else -> text("Backup is invalid: ${inspection.message}", "نسخه پشتیبان نامعتبر است: ${inspection.message}")
        }
        return inspection.copy(message = message)
    }
}
