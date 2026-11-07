# تطبيق مواقيت الصلاة (أندرويد)

## الطريقة 1: من الهاتف أو الكمبيوتر بدون تثبيت شيء (GitHub Actions)
1. أنشئ حساباً على github.com ومستودعاً جديداً (Repository).
2. ارفع كل محتويات هذا المجلد إليه (مع المجلد المخفي .github).
3. افتح تبويب Actions ثم Build APK ثم Run workflow.
4. بعد دقائق افتح العملية المكتملة ونزّل ملف prayer-app-apk من قسم Artifacts، وفكّ الضغط، وثبّت app-debug.apk.
   (فعّل "التثبيت من مصادر غير معروفة" إن طُلب.)

## الطريقة 2: Android Studio
npm install
npx cap add android
npx cap sync android
npx cap open android   # ثم Build > Build APK

## صوت الأذان في الإشعار (اختياري)
ضع ملف الأذان باسم adhan.mp3 داخل resources/raw/ قبل البناء.

## تعديل التطبيق
عدّل www/index.html ثم أعد البناء.
