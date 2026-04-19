const admin = require("firebase-admin");
const { onCall, HttpsError } = require("firebase-functions/v2/https");

admin.initializeApp();

const db = admin.firestore();

const ADMIN_UIDS = new Set([
  "KCKVGF5sJ7TfGWKAl0fRJziE4Ja2",
  "NFs38qPJAXXZFspS37nRhteROWn1",
]);

function requireAdmin(request) {
  const uid = request.auth?.uid || null;
  if (!uid || !ADMIN_UIDS.has(uid)) {
    throw new HttpsError("permission-denied", "Only admins can access this operation.");
  }
  return uid;
}

function normalizeCode(rawCode) {
  return String(rawCode || "").trim().toUpperCase().replace(/\s+/g, "");
}

function normalizeDeviceId(rawDeviceId) {
  return String(rawDeviceId || "").trim().toLowerCase();
}

function assertInput(code, deviceId) {
  if (!code) {
    throw new HttpsError("invalid-argument", "Access code is required.");
  }
  if (!deviceId) {
    throw new HttpsError("invalid-argument", "Device identifier is required.");
  }
}

function toMillis(value) {
  if (!value) {
    return null;
  }
  if (value.toDate) {
    return value.toDate().getTime();
  }
  if (typeof value === "number") {
    return value;
  }
  return null;
}

function isCodeInactive(codeData) {
  const status = String(codeData.status || "active").toLowerCase();
  return Boolean(codeData.revoked) || status !== "active";
}

function isCodeExpired(codeData) {
  const expiresAt = codeData.expiresAt;
  if (!expiresAt || !expiresAt.toDate) {
    return false;
  }
  return Date.now() > expiresAt.toDate().getTime();
}

async function addAccessEvent(eventType, payload) {
  await db.collection("access_events").add({
    event: eventType,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    ...payload,
  });
}

async function deleteActivationsInBatches(codeRef) {
  const pageSize = 400;
  let deleted = 0;

  while (true) {
    const snap = await codeRef.collection("activations").limit(pageSize).get();
    if (snap.empty) {
      break;
    }

    const batch = db.batch();
    snap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    deleted += snap.size;
  }

  return deleted;
}

exports.activateAccessCode = onCall(async (request) => {
  const code = normalizeCode(request.data?.code);
  const deviceId = normalizeDeviceId(request.data?.deviceId);
  assertInput(code, deviceId);

  const codeRef = db.collection("access_codes").doc(code);
  const activationRef = codeRef.collection("activations").doc(deviceId);

  const codeSnap = await codeRef.get();
  if (!codeSnap.exists) {
    await addAccessEvent("denied", { code, deviceId, reason: "code_not_found" });
    throw new HttpsError("not-found", "Access code not found.");
  }

  const codeData = codeSnap.data() || {};
  if (isCodeInactive(codeData)) {
    await addAccessEvent("denied", { code, deviceId, reason: "code_revoked_or_inactive" });
    throw new HttpsError("permission-denied", "This access code is no longer active.");
  }

  if (isCodeExpired(codeData)) {
    await addAccessEvent("denied", { code, deviceId, reason: "code_expired" });
    throw new HttpsError("failed-precondition", "This access code has expired.");
  }

  const maxDevices = Number(codeData.maxDevices || 20);
  const [activationSnap, activeSnap] = await Promise.all([
    activationRef.get(),
    codeRef.collection("activations").where("revoked", "==", false).get(),
  ]);

  if (!activationSnap.exists && activeSnap.size >= maxDevices) {
    await addAccessEvent("denied", { code, deviceId, reason: "device_limit_reached", maxDevices });
    throw new HttpsError("resource-exhausted", "Device limit reached for this access code.");
  }

  const batch = db.batch();
  const now = admin.firestore.FieldValue.serverTimestamp();

  const activationPayload = {
    deviceId,
    revoked: false,
    lastSeenAt: now,
  };
  if (!activationSnap.exists) {
    activationPayload.activatedAt = now;
  }

  batch.set(activationRef, activationPayload, { merge: true });
  batch.set(
    codeRef,
    {
      lastUsedAt: now,
    },
    { merge: true }
  );

  await batch.commit();
  await addAccessEvent("activate", {
    code,
    deviceId,
    maxDevices,
    byUid: request.auth?.uid || null,
  });

  return {
    ok: true,
    code,
    expiresAt: codeData.expiresAt || null,
    maxDevices,
  };
});

exports.verifyAccessCodeSession = onCall(async (request) => {
  const code = normalizeCode(request.data?.code);
  const deviceId = normalizeDeviceId(request.data?.deviceId);
  assertInput(code, deviceId);

  const codeRef = db.collection("access_codes").doc(code);
  const activationRef = codeRef.collection("activations").doc(deviceId);

  const [codeSnap, activationSnap] = await Promise.all([codeRef.get(), activationRef.get()]);

  if (!codeSnap.exists) {
    await addAccessEvent("denied", { code, deviceId, reason: "code_not_found_verify" });
    throw new HttpsError("not-found", "Access code no longer exists.");
  }

  const codeData = codeSnap.data() || {};
  if (isCodeInactive(codeData)) {
    await addAccessEvent("denied", { code, deviceId, reason: "code_revoked_verify" });
    throw new HttpsError("permission-denied", "Access code revoked.");
  }

  if (isCodeExpired(codeData)) {
    await addAccessEvent("denied", { code, deviceId, reason: "code_expired_verify" });
    throw new HttpsError("failed-precondition", "Access code expired.");
  }

  if (!activationSnap.exists || Boolean(activationSnap.get("revoked"))) {
    await addAccessEvent("denied", { code, deviceId, reason: "device_revoked_or_missing" });
    throw new HttpsError("permission-denied", "This device is not authorized.");
  }

  await activationRef.set(
    {
      lastSeenAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  await addAccessEvent("verify", {
    code,
    deviceId,
    byUid: request.auth?.uid || null,
  });

  return {
    ok: true,
    code,
    expiresAt: codeData.expiresAt || null,
  };
});

exports.revokeAccessCode = onCall(async (request) => {
  const uid = requireAdmin(request);

  const code = normalizeCode(request.data?.code);
  const deviceId = normalizeDeviceId(request.data?.deviceId);
  if (!code) {
    throw new HttpsError("invalid-argument", "Access code is required.");
  }

  const codeRef = db.collection("access_codes").doc(code);
  const codeSnap = await codeRef.get();
  if (!codeSnap.exists) {
    throw new HttpsError("not-found", "Access code not found.");
  }

  const batch = db.batch();
  const now = admin.firestore.FieldValue.serverTimestamp();

  if (deviceId) {
    const deviceRef = codeRef.collection("activations").doc(deviceId);
    batch.set(
      deviceRef,
      {
        revoked: true,
        revokedAt: now,
      },
      { merge: true }
    );
    await batch.commit();
    await addAccessEvent("revoke_device", { code, deviceId, byUid: uid });
    return { ok: true, scope: "device", code, deviceId };
  }

  batch.set(
    codeRef,
    {
      revoked: true,
      status: "revoked",
      revokedAt: now,
      revokedBy: uid,
    },
    { merge: true }
  );

  const activations = await codeRef.collection("activations").get();
  activations.forEach((doc) => {
    batch.set(
      doc.ref,
      {
        revoked: true,
        revokedAt: now,
      },
      { merge: true }
    );
  });

  await batch.commit();
  await addAccessEvent("revoke_code", { code, byUid: uid, affectedDevices: activations.size });

  return {
    ok: true,
    scope: "code",
    code,
    affectedDevices: activations.size,
  };
});

exports.deleteAccessCode = onCall(async (request) => {
  const uid = requireAdmin(request);
  const code = normalizeCode(request.data?.code);
  if (!code) {
    throw new HttpsError("invalid-argument", "Access code is required.");
  }

  const codeRef = db.collection("access_codes").doc(code);
  const codeSnap = await codeRef.get();
  if (!codeSnap.exists) {
    throw new HttpsError("not-found", "Access code not found.");
  }

  const deletedActivations = await deleteActivationsInBatches(codeRef);
  await codeRef.delete();

  await addAccessEvent("delete_code", {
    code,
    byUid: uid,
    deletedActivations,
  });

  return {
    ok: true,
    code,
    deletedActivations,
  };
});

exports.createAccessCode = onCall(async (request) => {
  const uid = requireAdmin(request);
  const code = normalizeCode(request.data?.code);
  if (!code) {
    throw new HttpsError("invalid-argument", "Access code is required.");
  }

  const maxDevicesRaw = Number(request.data?.maxDevices || 20);
  if (!Number.isInteger(maxDevicesRaw) || maxDevicesRaw < 1 || maxDevicesRaw > 500) {
    throw new HttpsError("invalid-argument", "maxDevices must be an integer between 1 and 500.");
  }

  const expiresAtMs = Number(request.data?.expiresAtMs || 0);
  if (!Number.isFinite(expiresAtMs) || expiresAtMs <= Date.now()) {
    throw new HttpsError("invalid-argument", "A valid future expiration date is required.");
  }

  const codeRef = db.collection("access_codes").doc(code);
  const existing = await codeRef.get();
  const now = admin.firestore.FieldValue.serverTimestamp();
  const payload = {
    status: "active",
    revoked: false,
    maxDevices: maxDevicesRaw,
    expiresAt: admin.firestore.Timestamp.fromMillis(expiresAtMs),
    updatedAt: now,
    updatedBy: uid,
  };

  if (!existing.exists) {
    payload.createdAt = now;
    payload.createdBy = uid;
  }

  await codeRef.set(payload, { merge: true });
  await addAccessEvent("create_code", {
    code,
    maxDevices: maxDevicesRaw,
    expiresAtMs,
    byUid: uid,
  });

  return {
    ok: true,
    code,
    maxDevices: maxDevicesRaw,
    expiresAtMs,
    created: !existing.exists,
  };
});

exports.getAccessCodeStatus = onCall(async (request) => {
  requireAdmin(request);
  const code = normalizeCode(request.data?.code);
  if (!code) {
    throw new HttpsError("invalid-argument", "Access code is required.");
  }

  const codeRef = db.collection("access_codes").doc(code);
  const codeSnap = await codeRef.get();
  if (!codeSnap.exists) {
    return {
      ok: true,
      exists: false,
      code,
    };
  }

  const codeData = codeSnap.data() || {};
  const [activeSnap, revokedSnap] = await Promise.all([
    codeRef.collection("activations").where("revoked", "==", false).get(),
    codeRef.collection("activations").where("revoked", "==", true).get(),
  ]);

  return {
    ok: true,
    exists: true,
    code,
    status: String(codeData.status || "active"),
    revoked: Boolean(codeData.revoked),
    maxDevices: Number(codeData.maxDevices || 20),
    expiresAtMs: toMillis(codeData.expiresAt),
    lastUsedAtMs: toMillis(codeData.lastUsedAt),
    activeDevices: activeSnap.size,
    revokedDevices: revokedSnap.size,
  };
});

exports.listAccessCodes = onCall(async (request) => {
  requireAdmin(request);
  const limit = Math.min(Math.max(Number(request.data?.limit || 30), 1), 100);

  const snap = await db.collection("access_codes").limit(limit).get();
  const codes = snap.docs.map((doc) => {
    const data = doc.data() || {};
    return {
      code: doc.id,
      status: String(data.status || "active"),
      revoked: Boolean(data.revoked),
      maxDevices: Number(data.maxDevices || 20),
      expiresAtMs: toMillis(data.expiresAt),
      createdAtMs: toMillis(data.createdAt),
      lastUsedAtMs: toMillis(data.lastUsedAt),
    };
  });

  codes.sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0));
  return { ok: true, codes };
});

