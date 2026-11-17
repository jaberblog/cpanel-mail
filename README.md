# مدیریت ایمیل cPanel (Kotlin + Jetpack Compose)

امکانات: ورود با API Token یا رمز، لیست حساب‌های ایمیل، ساخت، تغییر رمز، تغییر حجم، حذف.
از UAPI رسمی cPanel (ماژول Email) استفاده می‌کند.

## ساخت APK با GitHub Actions
1. یک repo جدید بسازید و محتوای این پوشه را push کنید (شاخه main).
2. تب Actions ← workflow به نام Build APK خودکار اجرا می‌شود (یا Run workflow).
3. بعد از پایان، از بخش Artifacts فایل `cpanel-mail-apk` را دانلود کنید (app-debug.apk).

## نکات
- API Token: cPanel ← Security ← Manage API Tokens.
- پورت پیش‌فرض 2083 است (HTTPS). اگر سرور گواهی self-signed دارد گزینه نادیده گرفتن SSL را روشن کنید.
- خروجی debug است و مستقیم نصب می‌شود؛ برای release باید signing تنظیم شود.
