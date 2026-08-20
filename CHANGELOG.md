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
- Custom snooze lengths: choose all six durations offered on a fired alert
  in Settings, shared with the alert screen.
- Yearly custom repeat interval ("every N years").

### Fixed
- Notification sound stopped playing after channel changes: versioned
  notification channel IDs so a changed sound actually takes effect (Android
  silently ignores sound changes to an existing channel, even recreated
  with the same ID).
- Notification sound and speech played at once with the ding sounding
  twice: added `setOnlyAlertOnce` plus a duplicate-delivery guard around
  alert firing.
- Repeat picker defaulted to today's date instead of the reminder's chosen
  date for weekly/monthly/yearly repeats.
- Repeat picker showed the wrong unit/amount ("2 days" instead of "10
  weeks") when reopening a custom-interval repeat.
- Re-alert nagging (repeat the alert until answered) stopped after the
  first alert when sync was on: a sync pull always overwrote the local
  in-progress nag state with null, since the server never sends it
  (it isn't in the sync JSON contract). A routine sync moments after an
  alert fired silently cancelled the nag cycle. Fixed by preserving the
  device's own nag state across a sync merge.
- A repeating reminder stayed pinned at the top of the list long after
  its time passed, still showing today instead of its real next
  occurrence: the list sorted by the raw stored due time, which only
  advances once the reminder is answered or nag-times-out. Now sorts by
  the actual next alert — the next occurrence, once the stored time has
  passed — so a fired daily reminder moves down to tomorrow's slot
  immediately instead of squatting at the top for up to an hour.
- Voice widget didn't work on phones with no Google services and no
  RecognitionService-based recognizer installed (e.g. GrapheneOS) — it
  only ever tried binding to a RecognitionService, which apps like FUTO
  Voice Input don't implement (they handle the older RECOGNIZE_SPEECH
  activity intent instead). `isRecognitionAvailable()` could also
  return true with nothing real bound, failing instantly with a raw
  "code 5" error. Now falls back to launching whatever app handles the
  RECOGNIZE_SPEECH intent as an activity when no RecognitionService is
  bound, so FUTO Voice Input and similar recognizers actually work; only
  shows the "install a recognizer" message when truly nothing is found.
- Re-alert nagging stopped after the first alert even with sync off: the
  duplicate-delivery guard added in 1.0.11 never forgot what had fired
  (its cleanup was cancelled with the alert service), and a nag re-alert
  carries the same reminder-and-time key as the first alert, so every
  re-alert after the first was swallowed as a "duplicate". The guard now
  expires entries by timestamp instead, with unit tests pinning it down.
- Reminders whose time passed while the phone was off never fired and
  silently stopped repeating: the alarm never went off, so nothing
  advanced the reminder to its next occurrence or re-armed it. After a
  reboot they're now delivered late (staggered so several don't talk
  over each other), which also rolls repeating reminders forward.
- A location reminder created or removed on the web dashboard now takes
  effect on the phone at the next sync instead of the next reboot.
- The alert service could crash the app on some phones when an alert
  arrived already-handled (deleted, quiet hours, nag timeout): Android
  requires every started alert to present itself promptly, including
  the ones that decide not to sound.
- The update dot no longer checks GitHub on every app open unless the
  opt-in automatic update check is switched on, matching what Settings
  promises about when the app phones out. Update downloads are also now
  refused unless they come from GitHub over HTTPS.
- An edit made on the phone in the moments right after a sync could
  stay stuck on the phone if its clock ran behind the server's; syncs
  now overlap a safety margin so nothing falls between the cracks.
- Removed a future data-loss landmine: a database schema change without
  a written migration now fails loudly at startup instead of silently
  wiping every reminder (the schema history is exported for writing
  real migrations), and Room's destructive fallback is gone.
- Saving a location reminder without picking a place is no longer
  possible (it could never have fired).
- Housekeeping: dead code from the removed ringing feature and unused
  icons, strings, imports, and test/build dependencies are gone; the
  Android 12+ approximate-location permission accompanies the precise
  one, so lint runs clean.

### Added (1.0.17)
- History: one-time reminders leave the main list once they're done and
  live under the new clock button in the top bar (between the update dot
  and Statistics). Look back over them, or tap one to give it a new date
  and bring it back — opening a finished reminder from History switches
  it back on and rolls a stale date forward to today, so "pick a date,
  Save" is the whole gesture. Matching History page on CHKT Server
  (1.1.14).

### Changed (unreleased)
- History now takes every ended reminder, not just one-times: a repeating
  or location reminder you switch off moves there too, instead of sitting
  greyed-out at the bottom of the main list. Reuse works the same way —
  open it, give it a date, and it's back.
