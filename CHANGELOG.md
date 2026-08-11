# Changelog

All notable changes to CHKT are recorded here.
Format: [Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.0.0] - unreleased

First release.

### Added
- Tags replace lists: a reminder wears any number of free-form tags, and
  the home screen shows everything coming up in time order with tag
  filtering. Version 1 export files still import (list names become tags).
- Lightmorphic brand yellow as the app accent.
- The voice widget treats a bare hour ("remind me at 10") as the next ten
  o'clock, morning or evening, whichever comes first.
- Reminder lists with per-reminder alert styles: ringtone + spoken, ringtone
  only, spoken only, notification only; optional tone before speech.
- Exact alarms with full-screen alerts, Do Not Disturb bypass, reboot
  survival, and snooze up to a day.
- Flexible repeats: daily, weekly (chosen days), monthly (day or last day),
  yearly, and custom intervals.
- Quiet hours.
- Tap-to-record home-screen widget with on-device structured phrase parsing.
- Location reminders (arrive/leave) via platform proximity alerts.
- Daily backups to a user-chosen folder; JSON and markdown export; JSON import.
- Optional sync with a self-hosted CHKT Server (off by default).
- Completion statistics for the last 30 days.
- Guided one-question-at-a-time flow for creating reminders, ending in a
  plain-words summary.
- Per-reminder controls: vibration, respect-or-cut-through Do Not Disturb,
  delete once dismissed, active toggle, and re-alerts every 1/2/5 minutes
  when unanswered with an automatic stop (15 min to 2 hours).
- Alert sound chosen per phone in Settings (notification sound picker).
- Update check against the project GitHub releases (manual, or opt-in
  daily check with a notification) and one-tap install of new versions.
