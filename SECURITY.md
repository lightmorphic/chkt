# Security policy

## Reporting a vulnerability

Email **security@lightmorphic.co.uk**. Please include what you found, how to
reproduce it, and what you think the impact is. You'll get a reply within a
week, and a fix or an honest explanation of why not.

Please don't open a public issue for a security problem until it's fixed.

## Supported versions

The latest release on F-Droid / GitHub is supported. Older versions won't
receive backported fixes.

## Scope notes

- CHKT stores everything on-device in the app's private storage. Device
  backups exclude the sync access key (the credential never rides a cloud
  backup); reminders themselves do back up.
- Sync is off by default. When enabled, it talks only to the server address
  the user enters — HTTPS, or plain HTTP for private networks (Tailscale,
  LAN), which the app warns about — authenticated with a per-device access
  key.
- The record widget's audio is processed by the device's own speech
  recognition service and never transmitted by CHKT.
