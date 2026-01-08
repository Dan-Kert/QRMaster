# 📷 QR Master

**QR Master** is a lightweight, high-performance QR code scanner for Android. Built with a focus on privacy and utility, it features a smart auto-connect system for WiFi QR codes, making network joining seamless and secure.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)

---
<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="200" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="200" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="200" />
</p>
## 🚀 Key Features

* **Smart WiFi Auto-Connect:** Automatically detects WiFi QR codes and connects you to the network.
    * *Android 10+ Support:* Uses the modern `WifiNetworkSuggestion` API for a system-integrated experience.
    * *Legacy Support:* Direct WiFi management for older Android versions.
* **Privacy-Centric:** No analytics, no trackers, and no unnecessary permissions. 
* **Built for FOSS:** Dedicated build flavors to ensure 100% open-source compliance for F-Droid users.
* **Clean UI:** Modern interface built with **Jetpack Compose**.
* **Multilingual:** Supports English, Russian, Romanian, Spanish, Arabic, and Chinese.

---

## 🏗 Project Structure (Build Flavors)

To balance performance with open-source principles, the project is divided into two build variants:

| Flavor | Scanning Engine | Target |
| :--- | :--- | :--- |
| **`foss`** | **ZXing** (100% Open Source) | F-Droid & Privacy-conscious users |
| **`google`** | **Google ML Kit** | Google Play Store users |

### How to build:
To build the FOSS version:
```bash
./gradlew assembleFossRelease
