# Tutor Diary

A simple, fully offline Android app for home tutors in India. Track students, attendance and unpaid fees, and send polite fee reminders on WhatsApp.

- No account, no internet, no ads, no tracking. Data stays on the phone.
- English and Hindi.
- Min Android 8.0 (API 26). Target/compile SDK 36.

---

## 1. Try it on your phone (test build)

Every time code is pushed to `main`, GitHub builds the app, runs all tests, and publishes a test APK.

1. On your Android phone, open: **https://github.com/amitvayuyantra/tutor-app/releases/tag/test-build**
2. Tap **TutorDiary-test.apk** to download it.
3. Open the downloaded file. If Android asks, allow "Install unknown apps" for your browser.
4. Install and open **Tutor Diary**.

Notes:
- The test app is called "Tutor Diary" and installs **next to** a future Play Store version (its ID ends in `.test`).
- It is signed with a **public test key** kept in this repo (`keystore/test.keystore`). Fine for testing. **Never** use it for the Play Store.
- New test builds install over the old one and keep your data.

## 2. Run it from source

1. Install **Android Studio** (latest stable).
2. *File → Open* → choose this folder. Wait for Gradle sync.
3. Press **Run ▶** with a phone (USB debugging on) or an emulator.

Command line:
```bash
./gradlew testDebugUnitTest   # run all tests
./gradlew assembleDebug       # test APK → app/build/outputs/apk/debug/app-debug.apk
```

## 3. Create a signed release App Bundle (for Google Play)

You need your own **upload key**. Make it once and keep it safe (lose it = you can't easily update the app).

**Step 1 – make the key** (needs Java's `keytool`, included with Android Studio):
```bash
keytool -genkeypair -v -keystore tutor-diary-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```
Keep the `.jks` file and both passwords somewhere safe (not in this repo).

**Step 2a – build on your computer:**
```bash
export RELEASE_KEYSTORE_PATH=/full/path/to/tutor-diary-upload.jks
export RELEASE_STORE_PASSWORD='your-store-password'
export RELEASE_KEY_ALIAS=upload
export RELEASE_KEY_PASSWORD='your-key-password'
./gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab` → upload it in Play Console.

**Step 2b – or let GitHub build it:** in the repo go to *Settings → Secrets and variables → Actions* and add:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | output of `base64 -w0 tutor-diary-upload.jks` (macOS: `base64 -i tutor-diary-upload.jks`) |
| `RELEASE_STORE_PASSWORD` | store password |
| `RELEASE_KEY_ALIAS` | `upload` |
| `RELEASE_KEY_PASSWORD` | key password |

The next build then signs the bundle. Download it from the run's **Artifacts** (`tutor-diary-outputs`).

Before each Play release, raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.

## 4. Permissions

The release app requests **no user-facing permissions**. Checked on the built release APK with `aapt dump permissions`:

| Permission | Why |
|---|---|
| `com.tutordiary.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added automatically by AndroidX. Internal, signature-level; lets the app's own components talk to each other safely. Not shown to users. |

Not requested: INTERNET, SMS, Call log, Contacts, Storage, Location, Photos. The manifest actively strips `INTERNET` and `ACCESS_NETWORK_STATE` if any library tries to add them. `allowBackup="false"` and data-extraction rules block cloud backup and device transfer.

## 5. How fees work (plain words)

- Each student gets **one fee per month**, created on the 1st of the month. The month they join is charged in full; so is the month they're archived.
- No fees before the join month or after the archive month. If an archived student is made active again, fees restart from that month (the gap is not charged).
- If the due day doesn't exist (e.g. 31 in February), the **last day of that month** is used.
- A fee is **due** until its due date and **overdue** from the next day.
- Changing a student's fee affects **next month onwards**. Past months keep their old amount.
- Opening the app any number of times never creates duplicate fees (also enforced by the database). If the app wasn't opened for months, all missing months are added next time.
- Payments aren't tied to a month: they pay off the **oldest unpaid month first**. Paying more than due shows an **advance (credit)**.
- **Balance = fees − waivers − payments.** A waiver is for one month, needs a reason, and can't exceed that month's fee.
- Money is stored as whole rupees (`Long`). No decimals anywhere.

## 6. WhatsApp reminders

The app opens `https://wa.me/<number>?text=<message>`. **Nothing is sent automatically** – the tutor must press Send in WhatsApp. The app records "reminder opened", which does **not** mean delivered. If WhatsApp can't be opened, the message can be copied instead. Templates (English and Hindi) can be edited in *Settings → Edit reminder messages*.

## 7. Backup

*Settings → Create backup* writes a versioned JSON file to a place you choose. *Restore backup* checks the whole file first, warns that it will replace everything, and restores in a single all-or-nothing database transaction. CSV exports for students and fee balances are also in Settings.

## 8. Project layout

```
app/src/main/java/com/tutordiary/app/
  domain/        pure logic: billing, ledger, money, phone, reminder text, CSV
  data/db/       Room entities, DAOs, database
  data/repo/     TutorRepository (all reads/writes, transactions)
  data/backup/   backup file format, validation
  ui/            Compose screens + ViewModels (home, students, attendance, batches, settings)
app/src/test/    unit tests (JVM) + database tests (Robolectric)
docs/            privacy policy and Play Data Safety drafts
```

Stack: Kotlin, Jetpack Compose (Material 3), MVVM, Room, Hilt, kotlinx.serialization.

## 9. Test results

TEST_RESULTS_PLACEHOLDER

## 10. Known limitations

KNOWN_LIMITATIONS_PLACEHOLDER
