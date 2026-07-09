# SOS Emergency Alert App

**Status:** Phase 3 Foundation Complete (Android)

Personal safety app that records video + audio + GPS and sends to emergency contacts when user presses and holds a button after being triggered via power button or gesture.

## Quick Start

```bash
git clone <repo>
cd "E_Projects/18. SOS"
./gradlew build
./gradlew installDebug  # Requires Android device/emulator
```

## Architecture

### Phase 1: Trigger System ✅ (Complete)
- **SOSForegroundService**: Always-running with persistent notification
- **SOSAccessibilityService**: Detects 4x rapid power button presses
- **TriggerManager**: State machine for press detection
- **SOSActivity**: Full-screen UI with large red button

### Phase 2: Recording Engine ✅ (Complete)
- **Camera2Recorder**: Dual-camera (front + rear) recording
  - 1920×1080 @ 30fps, 3 Mbps bitrate
  - HEVC primary, H.264 fallback
  - Graceful fallback to rear-only if concurrent unsupported (~23% of devices)
- **AudioRecorder**: AAC audio capture (128 kbps, 44.1kHz, mono)
- **GPSTracker**: Location logging at 5-second intervals
- **IncidentBuffer**: Encrypted local storage (AES-256-GCM via Keystore)
- **RecordingManager**: Orchestrates all components

### Phase 3: Upload Engine 🔄 (Foundation Done, Implementation TBD)
- **VideoChunker**: Splits video into 20MB chunks for email
- **ContactStorage**: SharedPreferences-based contact management
- **UploadManager**: Orchestrates upload workflow
- **FirebaseGPSPublisher**: Streams live GPS to Firebase Realtime DB

### Phase 4: Setup & Contacts (Next)
- **SetupActivity**: Emergency contact management (UI wired, upload pending)
- Contact verification via SMS/email
- Test mode (record locally without notifying)

### Phase 5: Hardening (Later)
- Anti-abuse UX patterns
- Legal/compliance review
- Store submission (Play Store, App Store)

---

## Current Implementation

### Working
- ✅ App foreground service with persistent notification
- ✅ Accessibility service listening for power button (4x rapid press)
- ✅ SOS activity with large red button (press-and-hold recording)
- ✅ Camera2 dual-camera recording with fallback
- ✅ Audio recording (AAC)
- ✅ GPS tracking (5-second cadence)
- ✅ Encrypted local incident buffer
- ✅ Contact data model + local storage (SharedPreferences)
- ✅ Video chunking for email delivery (20MB chunks)
- ✅ Firebase integration setup

### TODO
- [ ] **Email uploader**: SendGrid or SMTP integration for sending video chunks
- [ ] **Dropbox uploader**: OAuth2 integration for direct upload to contact's Dropbox
- [ ] **Contact notifications**: SMS/email alerts with live location link
- [ ] **Firebase incident dashboard**: Web UI for viewing incidents + downloading videos
- [ ] **Contact verification**: SMS/email token-based confirmation before adding
- [ ] **WorkManager**: Resume failed uploads on network recovery
- [ ] **Testing**: Device testing on Android 9/11/14/15, dual-camera compatibility matrix
- [ ] **UI polish**: Better error handling, progress indicators
- [ ] **Legal review**: Recording consent laws by jurisdiction
- [ ] **Store submission**: Play Store policies + privacy manifest

---

## Key Design Decisions

### Why Press-and-Hold Recording?
- Avoids false positives (accidental activation)
- User has full control (release = instant stop)
- Visible state (big red button = recording)
- Anti-abuse (can't be used covertly)

### Why Foreground Service Always Running?
- Ensures app is alive for trigger response
- Visible notification (transparency: victim always knows app is active)
- Persistent across reboots (BootReceiver)

### Why No Central Video Server?
- App is $10 flat fee, not subscription
- Minimal backend cost (Firebase free tier only for metadata/GPS)
- Video stored on contact's own cloud (Dropbox/Drive) or emailed
- No company server burden or privacy concerns

### Why Chunked Upload?
- Email has ~25MB attachment limit
- Allows resumable uploads on poor networks
- Partial evidence persists if phone is destroyed

### Why Firebase for GPS?
- Free tier (50K reads/day, 20K writes/day) supports thousands of users
- Real-time database enables live map for contacts
- Contact can view incident map via web link (no app needed)

---

## Permissions Required

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Testing Checklist

### Phase 1: Trigger
- [ ] Power button 4x rapid press detected on Android 9
- [ ] Power button 4x rapid press detected on Android 11
- [ ] Power button 4x rapid press detected on Android 14
- [ ] Power button 4x rapid press detected on Android 15
- [ ] SOS activity launches within 2 seconds
- [ ] Red button visible and responsive
- [ ] Accessibility service survives app backgrounding

### Phase 2: Recording
- [ ] Dual-camera recording on Samsung Galaxy S20+
- [ ] Dual-camera recording on Google Pixel 6
- [ ] Falls back to rear-only on single-camera device
- [ ] Audio records cleanly (test with ambient noise)
- [ ] GPS logs at 5-second intervals with accuracy
- [ ] Files encrypt to local storage
- [ ] Release button stops recording cleanly
- [ ] App survives 10+ minutes of recording without crash

### Phase 3: Upload
- [ ] Video chunks created and stored locally
- [ ] Email delivery via SendGrid (or SMTP) successful
- [ ] Dropbox upload works with OAuth token
- [ ] Firebase GPS updates appear on dashboard
- [ ] Contact receives SMS notification with live location link
- [ ] Upload resumes after network drop

### Phase 4: Contacts
- [ ] Add contact flow works
- [ ] Contact list updates in UI
- [ ] Verification SMS/email sent and received
- [ ] Verified flag updates on confirmation
- [ ] Test mode records without notifying

---

## Known Limitations

| Limitation | Why | Workaround |
|---|---|---|
| Power button detection 60-85% reliable | Accessibility API limitations | Volume button fallback (95%), gesture triggers |
| Only ~23% of Android devices support dual-camera concurrent | Hardware limitation | Graceful fallback to rear camera only |
| Video stops if app backgrounds | iOS/Android camera API design | Record only while foregrounded; audio can background |
| Email file size limits (~25MB) | Email provider restrictions | Chunk video into 20-25MB segments |
| GPS accuracy varies by location | GPS hardware + environment | Use FusedLocationProviderClient; fallback to last known good fix |

---

## Code Structure

```
src/main/kotlin/com/sos/app/
├── MainActivity.kt                 # Home screen
├── SOSActivity.kt                  # Full-screen SOS UI with red button
├── SetupActivity.kt                # Contact management
├── receiver/
│   └── BootReceiver.kt             # Rearm service on reboot
├── service/
│   ├── SOSForegroundService.kt     # Always-running background service
│   └── SOSAccessibilityService.kt  # Power button detection
├── trigger/
│   └── TriggerManager.kt           # State machine for press detection
├── recording/
│   ├── RecordingManager.kt         # Orchestrates recording
│   ├── Camera2Recorder.kt          # Dual-camera recording
│   ├── AudioRecorder.kt            # Audio capture
│   ├── GPSTracker.kt               # Location logging
│   └── IncidentBuffer.kt           # Encrypted local storage
├── upload/
│   ├── UploadManager.kt            # Orchestrates upload
│   ├── VideoChunker.kt             # Video chunking
│   ├── FirebaseGPSPublisher.kt     # Firebase streaming
│   └── (TODO: EmailUploader.kt, DropboxUploader.kt)
└── data/
    ├── Contact.kt                  # Contact data model
    └── ContactStorage.kt           # SharedPreferences storage
```

---

## Next Steps

### Immediate (This Week)
1. **Email uploader**: Integrate SendGrid API or SMTP for sending chunked video
2. **Contact notifications**: SMS (Twilio) + push (FCM) + email (SendGrid)
3. **Contact verification**: SMS/email token flow

### Short Term (Next 1-2 Weeks)
1. **Firebase dashboard**: Web UI for viewing incidents + live map
2. **Dropbox integration**: OAuth2 + direct upload to contact's account
3. **Device testing**: Android 9/11/14/15, dual-camera matrix

### Before Launch
1. **Legal review**: Recording consent laws (2-party vs 1-party states, GDPR)
2. **Anti-abuse UX**: Biometric lock, notifications on config changes, device-owner indicators
3. **Store compliance**: Play Store policies, privacy manifest, accessibility review
4. **Beta testing**: Real users, permission flows, error scenarios

---

## Debugging

### View logs
```bash
adb logcat -s "SOSActivity" "RecordingManager" "UploadManager"
```

### Test SOS Activity manually
- Open app → "Test SOS" button → Press and hold red button → Release

### Check incident buffer
```bash
adb shell "run-as com.sos.app find /data/data/com.sos.app/files/incidents -type f"
```

### View Firebase data (if configured)
- Console: https://console.firebase.google.com/ → Realtime Database → incidents

---

## Privacy & Security

- **Local storage**: AES-256-GCM via Android Keystore (hardware-backed StrongBox where available)
- **Transit**: TLS 1.3 with certificate pinning
- **Server data**: Firebase (metadata + GPS only, no video); video on contact's cloud or email
- **Video retention**: Auto-delete from server after 90 days (or user request)
- **Contact consent**: SMS/email verification before adding to alert list
- **No telemetry**: No analytics on recorded content

---

## Contributing

Git workflow:
```bash
git checkout -b feature/xyz
# Code
git commit -m "Description

Details.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"
git push
```

---

## License

TBD

---

**Last Updated:** July 8, 2026
