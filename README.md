# Chkt

**Talking reminders for Android.** Chkt rings, then *says* your reminder out
loud, so you know what needs doing without touching your phone.

A free, GPL-licensed [Lightmorphic](https://lightmorphic.co.uk) side project.
No ads, no tracking, no Google services, no account. Donation-supported.

## What it does

- **Spoken reminders** through your phone's text-to-speech engine, with a
  choice of alert styles per reminder: ringtone + speech, ringtone only,
  speech only, or a plain notification. Optional tone before the speech.
- **Alarms that actually fire**, exact alarms that survive reboots and
  battery savers, bypass Do Not Disturb, and show a full-screen alert like an
  alarm clock.
- **Multiple lists**, flexible repeats (daily, weekly, monthly, yearly,
  custom intervals), and snooze up to a day.
- **Tap-to-record widget**, a plain home-screen icon; tap it and say
  *"remind me at 2pm to feed the cat"*. Parsed on-device, nothing leaves
  your phone.
- **Location reminders**, when you arrive at or leave a place, using
  Android's own location services (works on de-Googled phones).
- **Quiet hours**, a do-not-disturb schedule for Chkt itself.
- **Backups and portability**, daily backup to a folder you choose, plus
  export/import as plain JSON or markdown. Your data is yours.
- **Optional sync**, off by default. Pair it with a self-hosted
  [Chkt Server](https://github.com/FOSSCharlie/chkt-server) and your phone
  and browser stay matched.
- **Simple statistics**, how consistently you complete what you set.

## Voice

Chkt speaks through whatever text-to-speech engine is installed. If your
phone doesn't have one, Chkt suggests
[SherpaTTS](https://f-droid.org/packages/org.woheller69.ttsengine/), a
free, offline voice engine on F-Droid. Any TTS engine works.

The record widget likewise uses your phone's speech recognition service if
one is installed (e.g. FUTO Voice Input).

## Permissions, honestly

| Permission | Why |
|---|---|
| Exact alarms | Reminders that fire on time, the whole point |
| Notifications, full-screen | The alert itself |
| Boot | Re-arm alarms after a restart |
| Microphone | Only while the record widget listens; audio never leaves the device |
| Location (optional) | Only if you set an arrive/leave reminder |
| Internet (optional) | Only used when you turn sync on |

## Building

```bash
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (platform 34). No proprietary
dependencies; builds cleanly for F-Droid.

## Licence

[GPL-3.0](LICENSE). Fork it, learn from it, improve it.
