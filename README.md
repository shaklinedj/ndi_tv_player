# 📺 Dreams NDI Player (Android TV)

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/shaklinedj/ndi_tv_player?color=blue&logo=github)](https://github.com/shaklinedj/ndi_tv_player/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/shaklinedj/ndi_tv_player/android.yml?branch=master&logo=github&label=build)](https://github.com/shaklinedj/ndi_tv_player/actions)
[![Platform](https://img.shields.io/badge/platform-Android%20TV-green?logo=android)](https://developer.android.com/tv/leanback)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

**Dreams NDI Player** is a high-performance NDI® 6 monitoring application designed specifically for Android TV and mobile devices. It provides low-latency, high-quality video playback from NDI sources across your local network.

---

## ✨ Features

- 🚀 **NDI 6 SDK Support:** Powered by the latest NDI 6 for optimal performance.
- 📡 **Automatic Discovery:** Instantly find NDI sources on your network using mDNS.
- 📺 **Leanback UI:** Optimized for Android TV navigation via remote control.
- 🖥️ **Full-Screen Playback:** Seamless landscape experience for monitoring and professional use.
- ⚡ **Low Latency:** Minimal delay optimized for live production environments.
- 🔧 **Native Performance:** Core logic implemented in C++ (JNI) for maximum efficiency.

---

## 🚀 Getting Started

### Installation
1. Download the latest APK from the [Releases](https://github.com/shaklinedj/ndi_tv_player/releases) page.
2. Sideload the APK onto your Android TV or mobile device.
3. Ensure your device is on the same network as your NDI sources.

### Requirements
- Android 7.0 (API level 24) or higher.
- A stable WiFi or Ethernet connection.
- Active NDI sources on the network.

---

## 🛠️ Technical Details

- **Language:** Kotlin & C++ (JNI)
- **Minimum SDK:** API 24 (Android 7.0)
- **Target SDK:** API 34 (Android 14)
- **NDI Version:** 6.x

### Permissions Required
- `INTERNET` - Network access.
- `ACCESS_WIFI_STATE` & `CHANGE_WIFI_MULTICAST_STATE` - Discovery of mDNS sources.
- `NEARBY_WIFI_DEVICES` - Required on Android 13+ for local device discovery.

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details. NDI® is a registered trademark of NDI.tv.

---

Developed by [shaklinedj](https://github.com/shaklinedj).
