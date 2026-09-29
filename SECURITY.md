# Security policy

## Reporting a vulnerability

Report it **privately** through GitHub:
**Security > Report a vulnerability** on
<https://github.com/vcasadei/trecos/security>. Don't open a public issue.

Include the app version, Android version and the steps to reproduce. You'll get
an acknowledgement within 7 days, and a fix or a plan within 30 days for
confirmed issues. Please give that time before disclosing publicly.

## Supported versions

| Version | Supported |
|---|---|
| Latest release | Yes |
| Older releases | No; update first |

## Scope

Trecos has no server. In scope: the Android app, its local database and files,
its export files, its Google Drive sync, and the release signing process.

## Things to know as a user

| Topic | What happens |
|---|---|
| Where data lives | On the phone, and in your own Google Drive only if you turn on sync |
| App lock | Uses the phone's own biometrics or PIN; there is no Trecos password |
| Database encryption | Optional. The key is kept in your Google Drive's hidden app folder so you can recover on a new phone. **If you lose access to that Google account, encrypted data can't be recovered** |
| Photos | Never encrypted; location data is removed when a photo is added |
| Export files | Never encrypted, even when database encryption is on; store them safely |
| Verifying a download | Each GitHub release lists `SHA256SUMS` and the signing certificate's SHA-256 fingerprint |
