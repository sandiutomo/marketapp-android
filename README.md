![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![MVVM](https://img.shields.io/badge/Architecture-MVVM-orange?style=flat-square)
![Firebase](https://img.shields.io/badge/Firebase-AI%20Enabled-FFCA28?style=flat-square&logo=firebase&logoColor=black)
![Analytics](https://img.shields.io/badge/Analytics-11%20SDKs-5A6AE8?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

---

# MarketApp | Analytics & Martech Sample App

A sample Android e-commerce app that showcases **multiple most popular analytics and martech platforms** in action: Firebase, Amplitude, Mixpanel, Braze, PostHog, and more.

**What makes this valuable:** A complete learning project that demonstrates how professional teams use the industry's leading analytics, CRM, push, and feature flagging platforms together in real applications. See how they work side-by-side without building everything from scratch.

---

## What You Get
![Firebase](https://img.shields.io/badge/Firebase-BOM%2033.14-FFCA28?style=flat-square&logo=firebase&logoColor=black)
![Amplitude](https://img.shields.io/badge/Amplitude-1.26-0078FF?style=flat-square)
![Mixpanel](https://img.shields.io/badge/Mixpanel-8.3-7856FF?style=flat-square)
![PostHog](https://img.shields.io/badge/PostHog-3.7-F54E00?style=flat-square&logo=posthog&logoColor=white)
![AppsFlyer](https://img.shields.io/badge/AppsFlyer-6.15-00B2A9?style=flat-square)
![Segment](https://img.shields.io/badge/Segment-1.20-52BD95?style=flat-square)
![Meta](https://img.shields.io/badge/Meta%20App%20Events-18.0-0866FF?style=flat-square&logo=meta&logoColor=white)
![Braze](https://img.shields.io/badge/Braze-33.1-FF6B6B?style=flat-square)
![OneSignal](https://img.shields.io/badge/OneSignal-5.6-E54B4D?style=flat-square)
![Statsig](https://img.shields.io/badge/Statsig-4.41-194BFB?style=flat-square)
![Clarity](https://img.shields.io/badge/Microsoft%20Clarity-3.8-00A4EF?style=flat-square&logo=microsoft&logoColor=white)

| Feature | Benefit |
|---|---|
| **One-command analytics** | Call `track()` once, 11 SDKs fire automatically |
| **Session replay** | Watch user behavior across Amplitude, Mixpanel, PostHog, Clarity |
| **Feature flags** | Control features without releasing new versions |
| **Deep links & attribution** | Track where users come from (push, email, social, organic) |
| **GDPR consent** | Toggle all tracking on/off with one tap |
| **Debug panel** | See SDK status and feature flags live in the app |

---

## Quick Start (5 minutes)

### Step 1: Get the code

**On Mac:**
1. Open **Terminal** (search for "Terminal" in Spotlight)
2. Copy and paste this command, then press **Enter**:
```bash
git clone https://github.com/sandiutomo/MarketApp.git
cd MarketApp
```

**On Windows:**
1. Open **Command Prompt** or **PowerShell** (search for "cmd" in Start menu)
2. Copy and paste this command, then press **Enter**:
```bash
git clone https://github.com/sandiutomo/MarketApp.git
cd MarketApp
```

**Expected response:**
```
Cloning into 'MarketApp'...
```

---

### Step 2: Add your API keys
All analytics platforms require credentials.

1. **Find the template file:**
    - Open the folder you just cloned (called `MarketApp`)
    - Look for `local.properties.example`

2. **Create your own keys file:**
    - Duplicate `local.properties.example` and rename it to `local.properties`
    - This file stays on your machine — never uploaded to GitHub

3. **Fill in your credentials:**
   Open `local.properties` in any text editor and add:
   ```properties
   AMPLITUDE_API_KEY=your_amplitude_key_here
   APPSFLYER_DEV_KEY=your_appsflyer_key_here
   BRAZE_API_KEY=your_braze_key_here
   POSTHOG_API_KEY=your_posthog_key_here
   MIXPANEL_TOKEN=your_mixpanel_token_here
   # ... and so on
   ```

---

### Step 3: Open in Android Studio

1. **Download Android Studio** (if you don't have it): [android.com/studio](https://developer.android.com/studio)
2. **Open the project:**
    - Launch Android Studio
    - Click **File → Open** (or **Open an Existing Project**)
    - Select the `MarketApp` folder
    - Wait for it to load (2-3 minutes first time)

3. **Let it build:**
    - You'll see a "Gradle sync" message at the bottom
    - Let it complete (grab coffee ☕)
    - Once it says "Build Successful" — you're ready

---

### Step 4: Run on device or emulator

**Using Android Emulator (easiest for first time):**
1. In Android Studio, click the **Play** button (green triangle, top toolbar)
2. Select "Pixel 5" or any emulator
3. Click **OK** — the app launches in 30 seconds

**Using your phone (more fun):**
1. Connect your Android phone via USB cable
2. Enable "Developer Mode" on your phone
    - Go to Settings → About → tap "Build Number" 7 times
3. Click the **Play** button in Android Studio
4. Select your phone from the list
5. Wait for the app to install

**What happens next:**
- App opens → You see the home screen with products
- Tap anything → Events fire to all 11 analytics platforms
- Check your analytics dashboards to see live data

---

## Next Steps

Want to dive deeper? Read **[TECHNICAL_README.md](TECHNICAL_README.md)** for:
- How all 11 analytics SDKs work together
- Feature flag setup across multiple platforms
- Deep linking and attribution tracking
- Session replay and user consent management

---

## License

Free to use for personal learning, testing, and demo purposes. Attribution appreciated. See LICENSE file for details.

**Note:** This is a living project. Analytics and martech SDKs evolve — some may be updated, removed, or added over time. Use this as a reference for current best practices, not as a static guide.

---


[![Sandi Utomo](https://img.shields.io/badge/Made%20by-Sandi%20Utomo%20😎-5A6AE8?style=flat-square&logo=github&logoColor=white)](https://github.com/sandiutomo)
[![Sandi Utomo](https://img.shields.io/badge/LinkedIn-Sandi%20Utomo-0A66C2?style=flat-square&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/sandiutomo/)