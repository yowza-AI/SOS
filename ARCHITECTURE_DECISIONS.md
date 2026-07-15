# Architectural Decision Records (ADRs)

Key design decisions and their trade-offs. Includes what was rejected and why.

---

## ADR-1: Native Android (Kotlin) Over Cross-Platform Framework

**Decision**: Build native Android with Kotlin, not Flutter/React Native/Xamarin.

**Why It Matters**: Power button detection and dual-camera recording are the app's core features. Cross-platform frameworks lack first-class support for both.

**Trade-offs**:

| Approach | Pros | Cons |
|----------|------|------|
| **Native Kotlin** | Direct Camera2 API | Separate iOS codebase |
| | Accessibility Service for power button | More maintenance burden |
| | Full control over threading/memory | |
| **Flutter** | Shared codebase (70% code) | No plugin for power button |
| | Faster iteration | Camera2 requires platform channels anyway |
| | | ~80% of work still native |
| **React Native** | Similar to Flutter | More platform channel boilerplate |
| | | JS/Bridge overhead in hot path |

**Decision Rationale**: The "shared code" benefit evaporates when the two hardest subsystems (trigger + recording) require native code anyway. Native + Kotlin is cleaner.

**Reconsidered for iOS**: iOS has different constraints entirely (no power button, no background video). A shared architecture makes sense; a shared codebase doesn't. See ADR-4.

---

## ADR-2: Accessibility Service for Power Button Detection

**Decision**: Use Accessibility Service (`AccessibilityService.onKeyEvent()`) to detect power button presses.

**Why It Matters**: Power button is the headline feature. Reliability directly impacts user safety.

**Constraints**:
- Android provides NO direct public API for power button access
- Accessibility Service is the only cross-device method
- Battery cost is significant (10-25% annual drain)

**Rejected Alternatives**:

| Method | Reliability | Battery Cost | Works Locked? | Why Rejected |
|--------|-------------|--------------|---------------|-------------|
| **Accessibility Service** | 60-85% | 10-25%/yr | Yes | ← Chosen |
| `BroadcastReceiver.ACTION_SCREEN_OFF` | 40-60% | 2-5%/yr | No | Too unreliable |
| Volume button listener | 95% | <1%/yr | Yes | Requires user retrain (users expect volume for vol) |
| OEM-specific SDKs | 90%+ | Varies | Maybe | Only works on Samsung/Xiaomi; fragments userbase |
| Motion sensors (detecting power press pattern) | <50% | High | Yes | Ambiguous with other hand movements |

**Why This Choice**: Accessibility Service is the only method that:
1. Works across devices (Samsung, Google, Xiaomi, Oppo, Vivo, etc.)
2. Works when phone is locked
3. Doesn't require app to be in foreground

**Battery Trade-off Accepted Because**:
- Users opt-in explicitly (disclosed during setup)
- 10-25% annual drain ≈ 30-60 mAh/day on modern batteries (acceptable)
- Alternative (always-polling motion sensors) drains more
- In emergency, battery drain is secondary to having the feature

**Fallback Strategy**: Volume button (95% reliable) is the *recommended primary* trigger. Power button is secondary. UX should guide users to volume button for reliability.

---

## ADR-3: Press-and-Hold Button vs. Tap-to-Arm

**Decision**: Recording is `press-and-hold` (button held the entire duration). Not `tap-to-start / tap-to-stop`.

**Why It Matters**: Minimizes false positives and user uncertainty about whether recording is active.

**Trade-offs**:

| Approach | Pros | Cons |
|----------|------|------|
| **Press-and-Hold** | Clear state (button held = recording) | Requires hand to stay on button |
| | No ambiguity | Can't use phone while recording |
| | Can't accidentally leave recording on | Tiring for long incidents |
| **Tap-to-Start/Stop** | Can use phone while recording | False positive risk (accidental tap) |
| | Works with injury (can't hold button) | Unclear if still recording without looking |
| | | UX confusion: tap to stop, but what if tap fails? |

**Why This Choice**:
- In an emergency, you don't have a free hand anyway
- Clarity > usability in safety contexts (users prefer "I know exactly what's happening" over "I have both hands free")
- Press-and-hold prevents accidental stops (victim can't accidentally tap "end")
- Visible state (button depressed = recording) reduces user doubt

**Alternative for Accessibility**: Could add alternative "double-tap to arm/disarm" for users with motor impairments. Future work.

---

## ADR-4: Separate iOS Implementation (Not Shared Codebase)

**Decision**: iOS will be a separate Kotlin/Swift implementation of the same architecture, not a cross-platform port.

**Why It Matters**: iOS and Android have fundamentally different constraints that affect core design.

**iOS Constraints vs. Android**:

| Feature | Android | iOS | Impact |
|---------|---------|-----|--------|
| **Power button access** | Accessibility API (60-85%) | Impossible (kernel-level) | Must redesign trigger |
| **Background video recording** | Allowed (with restrictions) | Not allowed | Must record only while foregrounded |
| **Concurrent dual-camera** | Possible (~23% of devices) | Possible (iPhone 11+) | Different detection mechanisms |
| **Accessibility Service** | Rich & powerful | Limited iOS equiv | Different detection strategy |
| **Background modes** | 11 available | Strict, limited | Different background survival strategy |

**Decision Matrix**:

| Approach | Shared Code | IOS-specific Work | Verdict |
|----------|-------------|------------------|---------|
| **Native separately** | 0% | 100% | Not efficient, but clean |
| **Cross-platform (Flutter)** | 70% | 30% | Looks good, but still requires 30% platform-specific code |
| **Shared Kotlin core (JNI)** | 40% | 60% | Complex; not worth it for this app size |

**Why Separate iOS**: The "shared architecture" mindset still applies (same incident flow, same upload strategy, same privacy model), but the implementation is iOS-native. This avoids:
- Bridge layer overhead
- Cross-platform framework constraints
- Debugging two systems through one abstraction

**Timeline**: Android is the priority (Phases 1-4). iOS will be a separate Phase 5+ effort, using the same architectural principles but native Swift.

---

## ADR-5: No Centralized Video Server (Use Contact's Own Cloud)

**Decision**: Video never stored on company servers. Stored on contact's Dropbox/Drive or in their email instead.

**Why It Matters**: This is a $10 app, not a subscription SaaS. Server costs would exceed revenue.

**Trade-offs**:

| Approach | Storage Cost | Privacy | Reliability | Complexity |
|----------|--------------|---------|-------------|------------|
| **No central server** | $0/user/year | User controls data | Depends on email/Dropbox reliability | Low (just chunking) |
| **S3 storage** | ~$0.50/user/year @1GB per incident | Company holds data | 99.99% uptime | Medium (need auth, dashboard) |
| **Custom backend** | ~$2/user/year | Company infrastructure | Depends on ops | High (build + maintain) |

**Why This Choice**:
- At $10/purchase, need 10-50+ customers just to break even on $0.50/yr server cost
- Contact already has Dropbox/Gmail → reuse infrastructure
- Evidence deletion is user-controlled (GDPR compliance built-in)
- No liability if our servers are breached (video is not on our servers)
- Firebase (metadata + GPS) is free tier ← acceptable trade-off

**What We DO Store**:
- GPS coordinates (lightweight)
- Incident metadata (timestamp, duration, contacts)
- Auto-delete after 90 days

**What We DON'T Store**:
- Video files
- Audio files
- Contact list details (only hashes for verification)

---

## ADR-6: Video Chunking (20MB) Over Stream Upload

**Decision**: Video is chunked into 20MB pieces for email delivery, not streamed as single upload.

**Why It Matters**: Email has hard limits (25MB); chunking enables resumable uploads and partial evidence recovery.

**Rejected Approach: Direct Streaming**:

| Method | Pros | Cons |
|--------|------|------|
| **Single stream to S3** | Fast initially | Fails on network drop → entire upload lost |
| | Simpler logic | Large video at 1080p ≈ 500MB-1GB → timeout risk |
| **Chunked 20MB** | Resumable | Slightly slower (overhead per chunk) |
| | Partial evidence if phone destroyed | Must manage multiple uploads in parallel |
| | Works with email size limits | |

**Why This Choice**:
- Mobile networks fail frequently (signal loss, tower handoff, data-to-WiFi switch)
- If a 1GB video upload fails 90% through, all 900MB is wasted
- With 20MB chunks, worst case loses only 20MB
- Email provider won't accept >25MB attachment anyway

**Implementation**: Each chunk is a separate email OR direct Dropbox upload. Chunking logic is simple (~200 lines).

---

## ADR-7: Firebase Realtime Database for GPS (Not Polling)

**Decision**: Use Firebase Realtime Database with push protocol for GPS updates, not polling or REST.

**Why It Matters**: Contacts need LIVE location (updates within 1-2 seconds), not stale data.

**Trade-offs**:

| Approach | Latency | Scalability | Cost | Complexity |
|----------|---------|-------------|------|------------|
| **Firebase Realtime (push)** | <500ms | 50K reads/day free | $0 up to 10K users | Medium (setup) |
| **REST polling (S3)** | 5-30 sec | Depends on poll interval | $1-5/yr | Low |
| **Dedicated WebSocket server** | <100ms | 100K users | $50+/yr | High (build ops) |
| **Simple S3 writes** | 1-2 sec (refresh) | N/A | $0.50/yr | Low |

**Why Firebase Realtime**: 
- Free tier is genuinely free (50K reads/day, 20K writes/day → supports thousands of concurrent incidents)
- Built-in real-time sync (contacts see map update instantly)
- No server to maintain
- Live map can be a simple web page (no app needed for contact)

**Scalability Math**:
- 1000 users, 10 incidents/user/year = 10K incidents/year
- Each incident: 50 GPS writes (5s intervals × ~250s average incident)
- Total writes: 500K/year = ~40K/month = 1.3K/day
- **Result: Still within free tier even at 10K concurrent users**

---

## ADR-8: Multi-Channel Notification (SMS + Email + Push)

**Decision**: Alerts go to contact via THREE channels in parallel: SMS, Email, Push. If one fails, others succeed.

**Why It Matters**: In emergencies, reliability > cost. If SMS fails (bad network), email still gets through.

**Rejected Approach: Single Channel Priority**:

| Method | Pros | Cons |
|--------|------|------|
| **SMS only** | Instant on any network | Unreliable (carriers throttle) |
| **Email only** | Reliable (Gmail retries) | Slow (minutes not seconds) |
| **Push only** | Fast if app installed | Fails if app not installed |
| **Multi-channel (chosen)** | Triple redundancy | 3x API cost |

**Why Multi-Channel**:
- **SMS**: Fast, works even without data (victim's friend can check SMS while driving)
- **Email**: Reliable, includes video links, creates searchable record
- **Push**: Instant if contact has app, bypasses email spam filters

**Cost Model**: 
- SendGrid: ~$0.50 per incident (cheap)
- Twilio: ~$0.01 per SMS (cheap)
- FCM: Free
- **Total: ~$0.50-$1 per incident**
- At $10 per app, break-even at 10 incidents per customer

**Acceptable because**: Multi-channel is a safety feature, not a bug. A victim's contact SHOULD receive notification via multiple paths.

---

## ADR-9: Local Encryption (Keystore) Over End-to-End

**Decision**: Local encryption via Android Keystore (AES-256-GCM). Optional E2E encryption as future enhancement, not MVP.

**Why It Matters**: MVP must ship fast. E2E adds complexity (key recovery problem unsolved).

**Trade-offs**:

| Approach | Security | Complexity | Key Recovery |
|----------|----------|------------|--------------|
| **Local only (Keystore)** | Good (app-level) | Low | N/A (keys on device) |
| **E2E (encrypted on server)** | Best (only contact can decrypt) | High | Unsolved (key recovery? escrow?) |

**Why Local Keystore for MVP**:
- Keystore keys are hardware-backed on Android 6+ (StrongBox)
- Files are encrypted to device storage (can't be read off-device)
- IF user loses phone, keys are lost, but evidence is ALREADY at contact (via email/Dropbox)
- E2E adds key recovery complexity: password-derived key? Server escrow? Contact recovery? All risky.

**Future E2E Path**: 
- User derives a recovery passphrase at onboarding (stored securely offline)
- Server holds encrypted key (encrypted with passphrase)
- Provides true E2E without key-recovery complexity
- But for MVP, local encryption is sufficient (video is on contact anyway)

---

## ADR-10: Foreground Service (Always-On Notification) Over Silent Background

**Decision**: App runs in foreground service with persistent, visible notification. Not silent background.

**Why It Matters**: Transparency. Victim should ALWAYS know the app is armed and ready.

**Why This Choice**:
- **Transparency**: Victim can see "🚨 SOS Ready" notification at all times. No hidden surveillance.
- **Reliability**: Android won't kill foreground services aggressively (respects user's explicit choice)
- **Honesty**: If abuse victim discovers covert tracking, trust is destroyed
- **Anti-abuse defense**: If abuser installs this as spying tool, victim can SEE it's running. Makes covert abuse harder.

**Trade-off**: Notification is always visible (can't hide). This is intentional, not a bug.

---

## ADR-11: State Machines for Resilience (Not Flags)

**Decision**: Use explicit state machines (TriggerManager, IncidentBuffer state) rather than boolean flags.

**Why It Matters**: In a safety app, state ambiguity is dangerous. State machines prevent impossible states.

**Example - Power Button Trigger**:

```
State: IDLE
  Event: power press 1 → State: PRESSED_1
  Event: timeout → State: IDLE

State: PRESSED_1 (within 2s window)
  Event: power press 2 → State: PRESSED_2
  Event: timeout → State: IDLE

State: PRESSED_2
  Event: power press 3 → State: PRESSED_3
  Event: timeout → State: IDLE

State: PRESSED_3
  Event: power press 4 → TRIGGER! Launch SOS Activity
```

**Why Not Flags**:
```kotlin
// Bad: Ambiguous states possible
var pressCount = 0
var lastPressTime = 0
// What if `pressCount = 4` but `lastPressTime` is old? Contradiction!
```

**State Machine Advantage**:
- Can't be in an impossible state (e.g., "recording and stopped simultaneously")
- Clear transitions make bugs obvious in code review
- Easier to debug (just log current state)

---

## Summary: Guiding Principle

Every decision above is driven by one principle:

**Constraint-Aware Design: Given the constraints, what's the simplest, most reliable solution?**

| Constraint | Decision |
|-----------|----------|
| $10 one-time price | No server storage (use contact's cloud) |
| Mobile networks fail | Chunked, resumable uploads |
| iOS/Android differ | Native implementations, shared architecture |
| Emergency context | State machines, not flags |
| User safety depends on us | Multi-channel notifications, transparent background |
| Power button is hard | Fallback to volume button (95% reliable) |

Recruitment context: These decisions show how to prioritize under constraints—a core skill for architects.

---

**Document Owner**: Architecture team  
**Last Updated**: July 2026  
**Status**: Frozen for Phases 1-4 | Open for review on Phase 5 decisions
