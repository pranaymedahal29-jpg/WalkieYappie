# WalkieYappie
Walkie-Yappie is an application where users communicate through BLE and wifi-direct. The purpose of this application is that it can be used to communicate off the grid where there is no cellular service, it works just like a walkie-talkie but it does not operate on radio signals. It was inspired when me and my friend where exploring a lake in chikkamaglur where there was no service though we were in clear sight of each other we couldn’t communicate thats when we thought of making an app that uses radio frequencies to communicate but since mobile phones do not have radio transmitters we settled for the next best option Wifi-Direct.

It works on the principle of direct device-to-device connection and transmits audio using Wi‑Fi Direct and Bluetooth Low Energy (BLE). Using a peer-to-peer (P2P) network, devices connect only after mutual approval, helping maintain privacy and security between users. This allows the app to create a local communication channel without depending on the internet or cellular network, making it useful in areas where regular connectivity is limited or unavailable. Once connected, users can communicate in real time through voice transmission, similar to a traditional walkie-talkie, but adapted for modern smartphones.

---
### Tech Stack & Requirements

- **Language**: Kotlin 1.9.24
- **UI Framework**: Jetpack Compose (Material 3)
- **Minimum SDK**: API Level 24 (Android 7.0)
- **Target SDK**: API Level 34 (Android 14)
- **Build System**: Android Gradle Plugin (AGP 8.4.2)

### Features

- Zero-Infrastructure P2P Mesh used to communicate anywhere in remote wilderness, underground, during natural disasters, or at crowded festivals without cell towers.
- Using Wifi-Direct to create a reliable local connection between nearby devices to enable instant voice communication with minimal latency(<100ms)
- Using BLE to discover and connect to new devices even when direct Wi-fi connection is not available yet
- Users can connect to each other only when they mutually approve of each others requests. This is more private and prevents unauthorised devices eavesdropping on conversations
- Users press and hold a button to transmit audio, this Push-To-Talk(PTT) feature makes the experience feel similar to a traditional walkie-talkie.
- Many devices can join the same local network and communicate as a group, which is very helpful out in the field.
- Communication is bound within the local P2P network, reducing the risk of public network exposure and is more reliable in offline environments where there is no external infrastructure
- Push notification for even when app is running in the background and also displays the current person speaking.
---
### Architecture and Network topology

![[Pasted image 20260819003342.png]]

### Installation
1. Download `WalkieYappie-v1.0.0.apk` attached to this release.
2. Tap the APK file on your Android device to install (allow *"Install from unknown sources"* if prompted).

or

1. **Clone the repository**:
   ```bash
   git clone https://github.com/pranaymedahal29-jpg/WalkieYappie.git
   cd WalkieYappie
   ```
2. **Open in Android Studio**:
   Open Android Studio (Jellyfish or newer) and sync Gradle project files.
3. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   The generated APK will be available at `app/build/outputs/apk/debug/app-debug.apk`.


- Open **WalkieYappie**, choose your custom Callsign (e.g. `ALPHA-1`), and grant requested permissions.
- Tap **SCAN** to discover nearby devices or receive connection requests!