### The Driver v1.17.1 - Cleaner request log

- **Only real requests are logged now.** Other inDrive screens were being saved as rides: the ride in progress ("Cancel ride", "Navigate", "Waiting time"), cancel and rating dialogs, balance / income / VAT / top-up / tier screens, notifications, and the "Wait for the reply" countdown. A screen is now treated as a request only when it really shows one (inDrive's request fields, or an Accept / Offer button).
- **The countdown is no longer taken as an address** ("31 sec."), so one request no longer becomes a new record every second.
- **Your existing log is cleaned automatically** the first time the app reads it after the update, so the analysis and recommendations reflect real requests only.
- Text snapshots of request screens no longer include map markers and internal ids.
- The floating badge no longer appears on those other screens either.
