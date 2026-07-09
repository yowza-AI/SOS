# SOS App Architecture

## Overview
Emergency safety app that records video + audio + GPS and sends to emergency contacts via email/Dropbox when user presses and holds a button after being triggered.

## Design

### Trigger System
- **Always-running foreground service** with persistent notification
- **Power button detection** via Accessibility Service (4 rapid presses = trigger)
- **Alternative triggers**: Volume button pattern, gesture, lock screen widget, Siri
- **Launches SOS Activity** when triggered

### Recording & UI
- **SOS Activity** appears full-screen with large red button
- **Press and hold = record** (front camera, rear camera, audio, GPS)
- **Release = stop** recording and start upload
- User sees real-time recording state (🔴 RECORDING, ✓ Uploaded)

### Data Flow
```
User Trigger (4x power button)
    ↓
Foreground Service + Accessibility Listener
    ↓
Launch SOS Activity (full-screen)
    ↓
User presses red button
    ↓
Start recording:
  • Front camera (Camera2 API)
  • Rear camera (concurrent if supported, graceful fallback to rear-only)
  • Audio (AudioRecord → AAC)
  • GPS (FusedLocationProviderClient, 5-second cadence)
    ↓
User releases button
    ↓
Stop recording → chunk video locally
    ↓
Upload chunks:
  • Contact's email (chunked via SMTP or SendGrid)
  • OR Contact's Dropbox (OAuth2, direct upload)
  • Stream GPS to Firebase Realtime Database
    ↓
Send SMS/Email notification to contacts:
  "Emergency alert from [Name]
   View live location: https://sos.app/incident/abc123
   Download video: [chunks in email]"
```

### Backend (Minimal)
**Firebase** (free tier):
- `incidents/{incidentId}` - Metadata (timestamp, duration, user, contacts)
- `gps_tracks/{incidentId}/{timestamp}` - Live GPS coordinates
- Live map dashboard (web, no login needed - token-based access)

**Contact's Own Storage**:
- Video files in Dropbox/Google Drive
- Email contains download links

## Architecture Components

### Android Layer
```
SOSAccessibilityService
  └─ Listens for power button
  └─ Detects 4-press pattern (TriggerManager)
  └─ Launches SOSActivity

SOSForegroundService
  └─ Persistent notification
  └─ Keeps app alive in background
  └─ Re-armed on boot

SOSActivity
  └─ Full-screen SOS UI
  └─ Large red button
  └─ Press-and-hold recording control

MainActivity
  └─ Setup (add emergency contacts)
  └─ Test mode

Recording Engine (Phase 2)
  └─ Camera2 dual-camera capture
  └─ AudioRecord + AAC encoding
  └─ GPS polling + local buffer

Upload Engine (Phase 2)
  └─ Video chunking (tus or custom)
  └─ Email chunking (SMTP/SendGrid)
  └─ Dropbox OAuth2 upload
  └─ Firebase GPS streaming
```

### Backend Layer (Minimal)
```
Firebase Realtime Database
  └─ GPS track updates (lightweight, no video)
  └─ Incident metadata

Simple Web Dashboard
  └─ Live map (reads GPS from Firebase)
  └─ Incident status
  └─ Shareable link (token-based, no auth required)

Contact Notification
  └─ SMS (Twilio or similar)
  └─ Email (SendGrid or Gmail SMTP)
  └─ Push (Firebase Cloud Messaging)
```

## Build Plan (Phases)

**Phase 1: Trigger System** (In Progress)
- ✅ Foreground Service
- ✅ Accessibility Service (power button detection)
- ✅ Trigger State Machine
- ✅ SOS Activity UI (red button)
- ⏳ Test on real device

**Phase 2: Recording Engine**
- Camera2 dual-camera recording
- Audio capture + AAC encoding
- GPS polling (5-second cadence)
- Local encrypted buffer

**Phase 3: Upload & Transmission**
- Video chunking
- Email delivery (SendGrid/SMTP)
- Dropbox OAuth2 integration
- Firebase GPS streaming
- Contact notification (SMS/push/email)

**Phase 4: Setup & Contacts**
- Contact management (add/remove/verify)
- Contact verification via SMS/email
- Test mode (record locally, don't notify)

**Phase 5: Hardening**
- Anti-abuse UX patterns (biometric lock, notifications on changes)
- Compliance & legal review
- Store submission (Play Store, App Store)

## Technical Decisions

**Why no central video server?**
- User pays $10 flat fee, not subscription
- Minimal backend cost (Firebase free tier only handles metadata)
- Video stored on contact's own cloud (Dropbox/Drive) or emailed

**Why Accessibility Service for power button?**
- Only reliable cross-device method for power button detection on Android
- 60-85% detection rate (trade-off accepted, fallback to volume button 95% reliable)
- Battery cost: 10-25% annually (disclosed to user, optional)

**Why foreground service always running?**
- Ensures app is alive for trigger
- Visible notification (transparency: victim always knows app is active)
- WorkManager handles deferred retries if phone dies mid-upload

**Why press-and-hold for recording?**
- Avoids false positives (accidental recording)
- User has full control (release = instant stop)
- Visible state (big red button = recording, no button = not recording)
- Anti-abuse (button visible on screen, cannot be covert)

## Security & Privacy Considerations

**Video Storage**: Contact's own cloud (Dropbox, Drive) or email — app company does not store video.

**GPS Tracking**: Firebase Realtime Database with encrypted transmission (TLS 1.3). Auto-delete incidents after 90 days.

**Contact Data**: Stored locally on device (encrypted via Android Keystore) and verified via SMS/email token before adding.

**Encryption**: 
- Local: AES-256-GCM with Keystore-derived key
- Transit: TLS 1.3 + certificate pinning
- Optional E2E: User can derive recovery passphrase for ultimate privacy (optional, not required for v1)

**Permissions**:
- Camera/Mic: Only active when user holds button (foreground recording)
- Location: Continuous during incident, then stopped
- Accessibility: Only for power button detection (disclosed clearly)

## Testing Strategy

**Phase 1 (Current)**:
- Test power button detection on Android 9/11/14/15 devices
- Test lock screen launch speed
- Test Accessibility Service install flow

**Phase 2**:
- Test dual-camera on ~5 devices (Samsung Galaxy, Pixel, Xiaomi, etc.)
- Test camera fallback (single camera if concurrent unsupported)
- Test video+audio sync under motion

**Phase 3**:
- Test upload chunking with network drops
- Test GPS accuracy and lag
- Test contact notification delivery (SMS, email, push)

**Phase 4**:
- Contact verification UX
- Test mode (record locally without notifying)
- Permissions flow for first-time users

## Known Limitations & Mitigations

| Limitation | Why | Mitigation |
|---|---|---|
| Power button 60-85% reliable | Accessibility API limits | Volume button fallback (95%), gesture trigger, Siri |
| Only ~23% of Android devices support dual-camera concurrent recording | Hardware limitation | Graceful fallback to rear camera only; detect at runtime |
| Video stops if app backgrounds | iOS/Android camera API design | Record while foregrounded only; audio can continue if app backgrounds |
| No live location on iOS (video only when foregrounded) | iOS privacy restrictions | Accept as product limitation; video records while app is foregrounded; GPS continues in background |
| Email file size limits (~25 MB) | Email providers' limitations | Chunk video into 20-25 MB segments, send multiple emails |
| GPS accuracy varies | GPS hardware + environment | Use FusedLocationProviderClient for accuracy estimation; fallback to last known good fix |

## Open Questions (To Resolve)

1. **Android FGS Camera Startup**: Can FGS with camera type be started from foreground context on Android 14+, or does system reject it? (Spike needed)
2. **E2E Key Recovery**: How to balance "evidence survives phone destruction" with "server never decrypts"? (Design choice: simple server-side encryption first, E2E as optional second step)
3. **Legal Jurisdiction**: Which states' recording consent laws apply? (User's location at time of recording, or app registration? Compliance needed before launch)
4. **Anti-Abuse UX**: Implement all 16 defense patterns or MVP first? (MVP: just the visible red button + periodic contact SMS reminders)
