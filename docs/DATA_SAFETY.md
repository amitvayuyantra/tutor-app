# Google Play Data Safety form – draft answers (version with logins)

> Draft only. Check every answer against the final app before submitting.

## Data collection and security
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all user data encrypted in transit? | **Yes** (Firebase uses HTTPS/TLS) |
| Do you provide a way for users to request that their data is deleted? | **Yes** – in-app (Remove access, delete student, stop seeing record) and by email for full account deletion. Play also requires a **web link** for account deletion requests – add one (e.g. a simple page with your email). |

## Data types collected
| Data type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Personal info → **Name** | Yes | No* | Required to sign in | Account management, App functionality |
| Personal info → **Email address** | Yes | No | Required to sign in | Account management |
| Personal info → **User IDs** | Yes | No | Required | Account management, App functionality |
| Financial info → **Other financial info** (fees due, payments, tutor UPI ID) | Yes | No* | Only for invited students | App functionality |
| App activity → **Other user-generated content** (attendance counts) | Yes | No* | Only for invited students | App functionality |

\* Showing a student's own record to that student/parent, at the tutor's request, is part of the app's function, not "sharing with a third party" in Play's definition. Firebase is a service provider, so it is not "sharing" either.

**Not collected:** location, contacts, photos, files, SMS, call logs, device IDs, advertising ID, crash logs, analytics.

**Processed only on the device (not "collected"):** parent phone numbers, notes, records of students who were not invited.

## Other Play Console sections
- **Ads:** No ads.
- **Target audience:** 18+ (tutors and parents). Not designed for children.
- **Account creation:** Yes, with Google sign-in. Account deletion must be available (see above).
- **Privacy policy URL:** required – host `docs/PRIVACY_POLICY.md` once filled in.
