# Firebase Functions - Access Control

This folder contains callable Cloud Functions used by the app access-code system.

## Functions

- `activateAccessCode` - validates access code, enforces max devices, and registers device activation.
- `verifyAccessCodeSession` - validates that code and device are still active/revoked/expired.
- `revokeAccessCode` - admin-only revoke by code or by device.
- `createAccessCode` - admin-only create/update access code policy (max devices, expiry).
- `getAccessCodeStatus` - admin-only status and activation counters for a code.
- `listAccessCodes` - admin-only listing of recent access codes.

## Firestore collections used

- `access_codes/{CODE}`
- `access_codes/{CODE}/activations/{deviceIdHash}`
- `access_events/{autoId}`

## Local setup

```bash
cd functions
npm install
```

## Deploy

Run from repo root:

```bash
firebase deploy --only functions
```

## Notes

- Admin UIDs are hardcoded in `index.js` under `ADMIN_UIDS`.
- Keep Firestore client access to `access_codes` disabled in production rules.

