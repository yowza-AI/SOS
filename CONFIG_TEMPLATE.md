# Configuration Guide

This document describes how to configure API keys and external services for the SOS app.

## Required API Keys

### 1. SendGrid (Email & Contact Notifications)

**Purpose:** Send video chunks via email + alert emails to contacts

**Get API Key:**
1. Create account at https://sendgrid.com
2. Go to Settings → API Keys
3. Create new API Key with "Mail Send" permission
4. Copy the key

**How to use:**
```kotlin
val uploadManager = UploadManager(
    context,
    sendgridApiKey = "SG.xxxxx_YOUR_API_KEY_HERE"
)
```

**Where to store:**
- **Development:** In `local.properties` (git-ignored)
- **Production:** Environment variable `SENDGRID_API_KEY`
- **CI/CD:** Secret manager (GitHub Secrets, etc.)

---

### 2. Twilio (SMS Notifications)

**Purpose:** Send SMS alerts to emergency contacts

**Get Credentials:**
1. Create account at https://twilio.com
2. Go to Console → Account SID (copy this)
3. Go to Console → Auth Token (copy this)
4. Get a Twilio phone number or verify your own

**How to use:**
```kotlin
val contactNotifier = ContactNotifier(
    context,
    twilioAccountSid = "AC_YOUR_ACCOUNT_SID",
    twilioAuthToken = "YOUR_AUTH_TOKEN",
    sendgridApiKey = "SG_YOUR_API_KEY"
)
```

**Where to store:**
- **Development:** In `local.properties` (git-ignored)
- **Production:** Environment variables
  - `TWILIO_ACCOUNT_SID`
  - `TWILIO_AUTH_TOKEN`
- **CI/CD:** Secret manager

---

### 3. Firebase (GPS Tracking & Dashboard)

**Purpose:** Real-time GPS tracking + incident dashboard for contacts

**Setup:**
1. Go to https://console.firebase.google.com
2. Create new project (or use existing)
3. Enable Realtime Database:
   - Database → Create Database
   - Start in test mode (or restrict to authenticated users)
4. Copy project ID from Project Settings

**How to use:**
```xml
<!-- In AndroidManifest.xml, Firebase auto-initializes from google-services.json -->
<!-- This is handled by Firebase initialization -->
```

**Where to store:**
- `google-services.json` (from Firebase Console)
- Place in `app/` directory (git-ignored by default)
- Download via Firebase Console → Project Settings → Download google-services.json

**Realtime Database Rules (Test Mode):**
```json
{
  "rules": {
    "incidents": {
      "$incidentId": {
        ".read": true,
        ".write": "root.child('incidents').child($incidentId).exists() || auth != null"
      }
    }
  }
}
```

---

### 4. Dropbox OAuth2 (Optional - Contact Direct Upload)

**Purpose:** Upload video directly to contact's Dropbox folder

**Setup:**
1. Create Dropbox App at https://www.dropbox.com/developers/apps
2. Create App → Scoped App → Full Dropbox → Read & Write
3. Generate access token (Settings → Generate access token)
4. Contact must authorize app + grant permission

**How to use:**
- Contacts add Dropbox token during emergency contact setup
- App stores token securely (Keystore) for later use

**In SetupActivity:**
```kotlin
// TODO: Add Dropbox OAuth2 flow to contact setup
// User taps "Connect Dropbox" → browser OAuth flow → token stored
```

---

## Setup Instructions

### 1. Create `local.properties`

```properties
# SendGrid
SENDGRID_API_KEY=SG.YOUR_KEY_HERE

# Twilio
TWILIO_ACCOUNT_SID=AC_YOUR_SID_HERE
TWILIO_AUTH_TOKEN=YOUR_TOKEN_HERE

# Firebase (auto-detected from google-services.json)
```

**Important:** Add `local.properties` to `.gitignore` (already done in this project)

### 2. Download Firebase Config

```bash
# Download google-services.json from Firebase Console
# Place in: app/ directory
# This file is already in .gitignore
```

### 3. Update SOSActivity to Load Config

```kotlin
// In SOSActivity or a config manager class:
val uploadManager = UploadManager(
    context,
    sendgridApiKey = BuildConfig.SENDGRID_API_KEY,
    twilioAccountSid = BuildConfig.TWILIO_ACCOUNT_SID,
    twilioAuthToken = BuildConfig.TWILIO_AUTH_TOKEN
)
```

### 4. Add Secrets to build.gradle.kts

```kotlin
android {
    buildTypes {
        debug {
            buildConfigField("String", "SENDGRID_API_KEY", "\"${localProperties.getProperty("SENDGRID_API_KEY", "")}\"")
            buildConfigField("String", "TWILIO_ACCOUNT_SID", "\"${localProperties.getProperty("TWILIO_ACCOUNT_SID", "")}\"")
            buildConfigField("String", "TWILIO_AUTH_TOKEN", "\"${localProperties.getProperty("TWILIO_AUTH_TOKEN", "")}\"")
        }
    }
}
```

---

## Testing Without Real APIs

### Mock Mode (for development)

```kotlin
// Create mock implementations for testing
class MockEmailUploader : EmailUploader(apiKey = "") {
    override fun sendChunks(...): List<EmailResult> {
        // Simulate success
        return chunks.map { EmailResult(true, "Mock sent") }
    }
}
```

### Use Test Credentials

- SendGrid: https://sendgrid.com/docs/for-developers/sending-email/sandbox-mode/
- Twilio: Use sandbox phone number (doesn't send real SMS)
- Firebase: Use Realtime Database emulator locally

---

## Production Deployment

### GitHub Secrets (for CI/CD)

1. Go to Repository → Settings → Secrets and variables → Actions
2. Add secrets:
   - `SENDGRID_API_KEY`
   - `TWILIO_ACCOUNT_SID`
   - `TWILIO_AUTH_TOKEN`

3. Update CI/CD workflow:

```yaml
# In .github/workflows/build.yml
env:
  SENDGRID_API_KEY: ${{ secrets.SENDGRID_API_KEY }}
  TWILIO_ACCOUNT_SID: ${{ secrets.TWILIO_ACCOUNT_SID }}
  TWILIO_AUTH_TOKEN: ${{ secrets.TWILIO_AUTH_TOKEN }}
```

### App Signing & Distribution

- Ensure API keys are **not** hardcoded in git
- Use Android Keystore for sensitive data
- Consider API key rotation every 90 days
- Monitor API usage (SendGrid dashboard, Twilio console)

---

## Environment Variables Checklist

- [ ] SENDGRID_API_KEY (for email delivery)
- [ ] TWILIO_ACCOUNT_SID (for SMS)
- [ ] TWILIO_AUTH_TOKEN (for SMS)
- [ ] Firebase project configured (google-services.json)
- [ ] Dropbox App ID (optional, for contact Dropbox integration)

---

## Troubleshooting

### Email not sending?
- Check SendGrid API key is valid
- Verify contact email address is correct
- Check SendGrid Activity dashboard for bounce/delivery errors

### SMS not sending?
- Verify Twilio phone number is active
- Check Twilio Account SID + Auth Token
- Confirm contact phone number format (E.164: +1234567890)

### Firebase GPS not updating?
- Verify google-services.json is in `app/` directory
- Check Firebase Realtime Database rules allow writes
- Ensure device has internet connectivity

### Dropbox upload failing?
- Verify contact Dropbox access token is valid
- Check token hasn't expired (Dropbox tokens can expire)
- Verify contact account has available storage

---

## Security Best Practices

1. **Never commit API keys to git** (use .gitignore, environment variables)
2. **Rotate keys regularly** (every 90 days recommended)
3. **Limit API key permissions** (use scoped tokens where possible)
4. **Monitor API usage** (watch for unusual spikes)
5. **Use HTTPS everywhere** (all APIs support TLS 1.3)
6. **Encrypt keys at rest** (Android Keystore)
7. **Use separate keys for dev/staging/prod**

---

## Cost Estimates (Monthly, at scale)

| Service | Usage | Cost |
|---------|-------|------|
| SendGrid | 1000 emails/month | Free (up to 100/day) or $10+ |
| Twilio | 1000 SMS/month | ~$0.01 per SMS = $10 |
| Firebase | 10K incidents/month | Free tier (50K reads/day) or $1-10 |
| Dropbox | Per-user storage | Contact pays (user's account) |

**Total monthly cost:** $20-50 at moderate scale (1000 active users, 10 incidents/user/month)

With $10 app purchase price, costs are covered after ~2-5 customers.

---

Last updated: July 8, 2026
