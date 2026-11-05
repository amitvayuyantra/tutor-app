# Google Play Data Safety form – draft answers

> Draft only. Check every answer against the final app before submitting.
> Google's rule: data that is processed only on the device and never sent off it does **not** count as "collected".

## Data collection and security
| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | The app has no INTERNET permission and no SDKs that send data. All data stays on the device. |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no data collected) | Nothing is transmitted. |
| Do you provide a way for users to request that their data is deleted? | Not applicable (no data collected). Users can delete students in the app, or clear app storage / uninstall. | |

## Data types
Mark **none** as collected or shared.

For your own notes, the app stores these **on the device only**:
- Personal info: name (tutor, student, parent), phone number (parent's WhatsApp), other info (notes).
- Financial info: other financial info (fees, payments, UPI ID of the tutor).
- App activity: none tracked.

## User-initiated sharing (not "sharing" under Play's definition)
- Opening WhatsApp with a prefilled message: the user presses Send in WhatsApp.
- Saving a backup / CSV file to a location the user picks.

These are actions the user starts, so they are not "sharing" on the form. You may mention them in the store description.

## Other Play Console sections
- **Ads:** No ads.
- **Target audience:** Adults (tutors). The app is not designed for children. Do not select under-13 age groups.
- **Permissions declared in the manifest:** none that are user-facing (see README → Permissions).
- **Privacy policy URL:** required — host `docs/PRIVACY_POLICY.md` somewhere public (e.g. GitHub Pages) once filled in.
