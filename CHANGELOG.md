# Changelog

All notable changes to CHKT are recorded here.
Format: [Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.0.0 – 1.0.18]

The first public releases, recorded here as one era.

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

## [1.0.17]

### Added
- History: one-time reminders leave the main list once they're done and
  live under the new clock button in the top bar (between the update dot
  and Statistics). Look back over them, or tap one to give it a new date
  and bring it back — opening a finished reminder from History switches
  it back on and rolls a stale date forward to today, so "pick a date,
  Save" is the whole gesture. Matching History page on CHKT Server
  (1.1.14).

## [1.0.18]

### Changed
- History now takes every ended reminder, not just one-times: a repeating
  or location reminder you switch off moves there too, instead of sitting
  greyed-out at the bottom of the main list. Reuse works the same way —
  open it, give it a date, and it's back.
- Sync settings: the Test connection result now appears on one status
  line just under the Sync switch — the same place that shows "Active,
  last synced …" — instead of below the button at the bottom of the
  card, where the keyboard pushed it off the screen and a test looked
  like it had done nothing. A successful test also switches sync on by
  itself (and says so), rather than leaving one more tap between a
  proven connection and sync actually running. Editing the address or
  key clears a stale result.

## [1.0.19]

### Fixed
- The notification sound and the spoken reminder played over each other,
  and on nag re-alerts or an alert after a snooze the sound often didn't
  play at all. Both came from the sound belonging to the notification
  channel: Android plays a channel's sound the instant the notification
  is posted (so it landed on top of the voice, not before it), and only
  when the notification first appears — every later alert reusing the
  same still-visible notification was silent, which `setOnlyAlertOnce`
  made unconditional. CHKT now plays the sound itself and waits for it
  to finish before speaking, so every alert of a reminder is the same:
  sound, short pause, voice — first alert, each re-alert, and after a
  snooze alike. Voice-only and Notification-only reminders are unchanged
  in what they play. The notification channels are silent now, so the
  per-generation "alarms_v…"/"polite_v…" channels that existed only to
  change a channel sound are gone from Android's notification settings;
  the sound is still picked in CHKT's own Settings and applies from the
  next alert.

## [1.0.20]

### Added
- Reminders have a length, so they can be published to a calendar as a
  block rather than a moment. It defaults to nothing — a plain reminder
  is still a point in time — and never changes how or when the alert
  happens. Set it under "How long it takes" when a reminder has a date.
  Pairs with CalDAV on CHKT Server, where your reminders become a
  calendar you can subscribe to from any calendar app on any device, and
  anything you add to that calendar comes back as a reminder.

## [1.0.21]

### Changed
- Tags are lowercase. "Cal" and "cal" were two tags that looked identical
  in a list and behaved differently everywhere else; now there's only one
  of them, decided in the one place every save passes through — the edit
  screen, the voice widget and an import alike. Matches CHKT Server 1.1.20,
  so a reminder edited on either side comes out looking the same.
- The tag box suggests the tags you already have and turns each pick into a
  chip you can tap to remove. A word that isn't a tag yet takes a
  deliberate "add as a new tag" button, so a typo can't quietly become a
  tag that sits in the list forever looking almost right.

## [1.0.22]

### Added
- Pull down on the reminder list to sync with your server right now,
  instead of waiting for the hourly background pass. The natural gesture
  after tagging something on the phone that you want to see on the web —
  or the other way round. A message says how it went; with sync off it
  says that and changes nothing.

## [1.0.23]

### Added
- Tapping the version number in the top bar opens the project page in your
  browser, matching the version badge on CHKT Server's web pages.

## [1.0.24]

### Added
- Alert diagnostics in Settings: a log of what the last alerts actually
  did — the sound starting, finishing or failing, the voice speaking —
  for chasing down a phone where part of an alert stays quiet.
- If the chosen notification sound won't open when an alert fires, the
  alert now falls back to the system default sound instead of skipping
  the ding.

## [1.0.25]

Security-and-quality audit release: two independent reviews of the whole
codebase, every finding verified and fixed or consciously accepted.

### Fixed
- The duplicate-alarm guard could be defeated by its own bookkeeping: it
  keyed on the reminder's due time, which advancing to the next occurrence
  rewrites, so a duplicated delivery arriving moments later computed a
  different key and sounded a second full alert. The guard now keys on the
  reminder alone — its window is seconds, the shortest re-alert interval
  is a minute, so nothing legitimate collides.
- Pull-to-refresh could leave the spinner stuck forever if sync failed in
  an unexpected way; it now always stops.

### Changed (security hardening)
- The sync access key no longer rides device backups: reminders back up,
  the credential doesn't.
- The access key field is masked like the password it is.
- A plain http:// server address gets a clear warning (fine on Tailscale
  or your LAN, unencrypted anywhere else), and non-web addresses are
  refused outright.
- The updater re-checks its GitHub-over-HTTPS rule on every redirect hop,
  not just the first URL.
- Sync responses and imported backup files are size-capped instead of
  read without limit.

### Changed (housekeeping)
- README describes the app that exists (three alert styles, tags, the
  calendar) and documents the release-signing convention; SECURITY.md
  tells the truth about plain-HTTP sync and the backup exclusion; this
  changelog has proper per-version sections again.
- Tag helpers moved from the UI package to domain, the edit screen uses
  the one normalizer, dead code and unused dependency declarations are
  gone, and quiet-hours edge cases got tests.

## [1.0.26]

### Changed
- Tapping the version number now opens chkt.org, the project's site,
  instead of the GitHub repository. Update checks still use GitHub.

## [1.0.27]

### Changed
- Sync catches up much faster. The background pass runs every 15 minutes
  (Android's floor) instead of hourly, and opening the app syncs
  immediately — so a reminder created on your calendar or the web appears
  within seconds of picking the phone up, not up to an hour later.
  Existing installs pick the new schedule up on first open. Truly instant
  push without Google services would need UnifiedPush; on the list.
