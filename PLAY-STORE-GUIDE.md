# Android version: build and publish on Google Play

## 1. Open the project (one time)
1. Install **Android Studio** (free) from developer.android.com/studio.
2. Open Android Studio → **Open** → choose this `StudyHub-Android` folder.
3. Wait for "Gradle sync" to finish (bottom bar). The first time it downloads the Android tools (a few minutes).
   - If it offers to **upgrade the Android Gradle Plugin**, click **Upgrade** / accept the recommended steps.
4. Try it: **Tools → Device Manager → Create virtual device** (e.g. Pixel 9), then press the green **▶ Run**.
   Or plug in an Android phone with USB debugging turned on.

## 2. Before publishing: set the name
- App name: `app/src/main/res/values/strings.xml` → `app_name`.
- Permanent app ID (can't change after the first upload): `app/build.gradle.kts` → `applicationId` and `namespace`
  (currently `com.studyhub.app`; e.g. `com.yourname.appname`).
  Tell Claude the final name and it will update both and the package folders.

## 3. Google Play Console account
1. Go to play.google.com/console and sign up (one-time **US$25** fee). Choose **Personal** account.
2. Verify your identity and an Android device (the console walks you through it).

## 4. Build the file Google wants (.aab)
Android Studio → **Build → Generate Signed App Bundle / APK → Android App Bundle**
→ **Create new…** key store (save the file + passwords somewhere safe; you need them for every update)
→ **release** → Finish. The file appears in `app/release/app-release.aab`.

## 5. Create the app in Play Console
- **Create app** → name, language, App, Free.
- **Store listing**: short + full description, 512×512 icon (`play-store-icon-512.png` in this folder),
  a 1024×500 feature graphic, and 2–8 phone screenshots.
- **App content**: privacy policy URL (text below), Data safety (**no data collected or shared**), ads (**no**),
  content rating questionnaire, target audience (18+ or 13+), news app declaration (**not** a news publisher; it links to Google News).

## 6. Required test (new personal accounts)
New personal accounts must run a **closed test with at least 12 testers for 14 days in a row** before going public.
- Testing → **Closed testing** → create a track → upload the .aab → add testers' Gmail addresses (classmates!) → share the opt-in link.
- Testers install from the Play Store link and keep it installed and use it for 14 days.

## 7. Go live
After 14 days: Dashboard → **Apply for production access** → once approved, **Production → Create release** → upload the .aab → submit for review.

---

## Privacy policy (paste into a public page, e.g. a Google Doc set to "Anyone with the link" or a free site)

**Privacy Policy for [App Name]**

[App Name] does not collect, store on any server, or share any personal information.

- The Canvas calendar link you enter, your name, and the items you mark as done are saved only on your device.
- The app connects to the internet only to download your Canvas calendar from the link you provide and public news headlines from Google News.
- No accounts, analytics, advertising, or tracking are used.
- Uninstalling the app deletes all of its data.

Contact: [your email]
