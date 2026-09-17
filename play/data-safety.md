# Data safety form

Answers for **Policy > App content > Data safety**.

## Data collection and security

- **Does your app collect or share any of the required user data types?** No.
- **Is all of the user data collected by your app encrypted in transit?** Not applicable: no data is collected or transmitted.
- **Do you provide a way for users to request that their data be deleted?** Not applicable: no data is collected.

## Why "no collection" is accurate

- Play's definition of collection covers data sent off the device. Rubify processes screen images **only on the device**, in memory, and discards them after drawing the pinyin. Nothing leaves the device.
- The app has no `INTERNET` or network-state permission. ML Kit's telemetry library asks for them, and the manifest removes them. A build check (`verify<Variant>NoNetworkPermission`) fails if any network permission reappears.
- No analytics, crash reporting, ads or accounts.
- Backup and device-to-device transfer are disabled (`backup_rules.xml`, `data_extraction_rules.xml`).
- What the app stores on the device: the disclosure consent flag and the bubble's position. Neither is personal data.

## Revisit this form if

- a dependency is added or updated (check the merged manifest and the library's own data safety guidance), or
- any feature ever sends data off the device. That would first need the user's approval (AGENTS.md).
