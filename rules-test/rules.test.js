// Checks firestore.rules against the real Firestore emulator.
// Run: npx firebase emulators:exec --only firestore --project demo-tutor-diary "npm --prefix rules-test test"
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, getDocs,
  writeBatch, runTransaction, arrayUnion, arrayRemove,
} from 'firebase/firestore';

let env;
const TUTOR = 'tutorA';
const OTHER_TUTOR = 'tutorB';
const PARENT = 'parent1';
const STRANGER = 'stranger';
const KEY = `${TUTOR}_7`;
const CODE = 'ABC234';

const summary = (tutorUid = TUTOR) => ({
  tutorUid, studentName: 'Aarav', parentName: 'Mr Mehta', tutorName: 'Priya', upiId: 'priya@okaxis',
  monthlyFee: 1500, dueDay: 5, balance: 3000, unpaid: [], payments: [], attendance: [], archived: false, updatedOn: 1,
});

const db = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();

// What the app does when the tutor makes a code.
async function tutorInvites(uid = TUTOR, key = KEY, code = CODE) {
  const d = db(uid);
  const b = writeBatch(d);
  b.set(doc(d, 'students', key), { ...summary(uid), inviteCode: code }, { merge: true });
  b.set(doc(d, 'invites', code), { tutorUid: uid, studentKey: key, claimedBy: null });
  return b.commit();
}

// What the app does when a student/parent types the code.
function claim(uid, code = CODE) {
  const d = db(uid);
  return runTransaction(d, async (tx) => {
    const inviteRef = doc(d, 'invites', code);
    const invite = await tx.get(inviteRef);
    if (!invite.exists()) throw new Error('not found');
    const { claimedBy, studentKey } = invite.data();
    if (claimedBy && claimedBy !== uid) throw new Error('used');
    if (!claimedBy) tx.update(inviteRef, { claimedBy: uid });
    tx.update(doc(d, 'students', studentKey), { linkedUids: arrayUnion(uid) });
  });
}

// Tries to join while skipping the app's own checks, to prove the server blocks it.
function forceClaim(uid, code = CODE, key = KEY, touchInvite = true) {
  const d = db(uid);
  return runTransaction(d, async (tx) => {
    await tx.get(doc(d, 'invites', code));
    if (touchInvite) tx.update(doc(d, 'invites', code), { claimedBy: uid });
    tx.update(doc(d, 'students', key), { linkedUids: arrayUnion(uid) });
  });
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-tutor-diary',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

test('tutor can create, update and delete their own shared record', async () => {
  await assertSucceeds(tutorInvites());
  await assertSucceeds(updateDoc(doc(db(TUTOR), 'students', KEY), { balance: 1500 }));
  await assertSucceeds(getDoc(doc(db(TUTOR), 'students', KEY)));
  await assertSucceeds(deleteDoc(doc(db(TUTOR), 'students', KEY)));
});

test('a tutor cannot write a record in someone else\'s name', async () => {
  await assertFails(setDoc(doc(db(OTHER_TUTOR), 'students', KEY), summary(TUTOR)));
  await assertFails(setDoc(doc(db(OTHER_TUTOR), 'students', KEY), summary(OTHER_TUTOR)));
  await tutorInvites();
  await assertFails(updateDoc(doc(db(OTHER_TUTOR), 'students', KEY), { balance: 0 }));
  await assertFails(updateDoc(doc(db(TUTOR), 'students', KEY), { tutorUid: OTHER_TUTOR }));
  await assertFails(deleteDoc(doc(db(OTHER_TUTOR), 'students', KEY)));
});

test('nobody signed out can read or write anything', async () => {
  await tutorInvites();
  await assertFails(getDoc(doc(db(null), 'students', KEY)));
  await assertFails(getDoc(doc(db(null), 'invites', CODE)));
  await assertFails(setDoc(doc(db(null), 'students', 'x_1'), summary('x')));
});

test('a stranger cannot read a record or list invite codes', async () => {
  await tutorInvites();
  await assertFails(getDoc(doc(db(STRANGER), 'students', KEY)));
  await assertFails(getDocs(collection(db(STRANGER), 'invites')));
  await assertFails(getDocs(collection(db(STRANGER), 'students')));
});

test('parent joins with the code and can then read only that record', async () => {
  await tutorInvites();
  await assertSucceeds(claim(PARENT));
  await assertSucceeds(getDoc(doc(db(PARENT), 'students', KEY)));
  const q = query(collection(db(PARENT), 'students'), where('linkedUids', 'array-contains', PARENT));
  const snap = await assertSucceeds(getDocs(q));
  if (snap.size !== 1) throw new Error(`expected 1 record, got ${snap.size}`);
});

test('a used code cannot be used by a second person', async () => {
  await tutorInvites();
  await claim(PARENT);
  await assertFails(forceClaim(STRANGER));
  await assertFails(forceClaim(STRANGER, CODE, KEY, false));
  await assertFails(getDoc(doc(db(STRANGER), 'students', KEY)));
});

test('the same person can use their code again without error', async () => {
  await tutorInvites();
  await claim(PARENT);
  await assertSucceeds(claim(PARENT));
});

test('joining is impossible without claiming a code', async () => {
  await tutorInvites();
  await assertFails(updateDoc(doc(db(STRANGER), 'students', KEY), { linkedUids: arrayUnion(STRANGER) }));
});

test('a linked parent cannot change fees or add other people', async () => {
  await tutorInvites();
  await claim(PARENT);
  await assertFails(updateDoc(doc(db(PARENT), 'students', KEY), { balance: 0 }));
  await assertFails(updateDoc(doc(db(PARENT), 'students', KEY), { linkedUids: arrayUnion(STRANGER) }));
  await assertFails(deleteDoc(doc(db(PARENT), 'students', KEY)));
});

test('a linked parent can stop seeing the record', async () => {
  await tutorInvites();
  await claim(PARENT);
  await assertSucceeds(updateDoc(doc(db(PARENT), 'students', KEY), { linkedUids: arrayRemove(PARENT) }));
  await assertFails(getDoc(doc(db(PARENT), 'students', KEY)));
});

test('tutor can remove access; tutor cannot sneak extra people in', async () => {
  await tutorInvites();
  await claim(PARENT);
  await assertFails(updateDoc(doc(db(TUTOR), 'students', KEY), { linkedUids: arrayUnion(STRANGER) }));
  const d = db(TUTOR);
  const b = writeBatch(d);
  b.set(doc(d, 'students', KEY), { linkedUids: [], inviteCode: null }, { merge: true });
  b.delete(doc(d, 'invites', CODE));
  await assertSucceeds(b.commit());
  await assertFails(getDoc(doc(db(PARENT), 'students', KEY)));
});

test('an old code stops working after the tutor makes a new one', async () => {
  await tutorInvites();
  const d = db(TUTOR);
  const b = writeBatch(d);
  b.delete(doc(d, 'invites', CODE));
  b.set(doc(d, 'students', KEY), { inviteCode: 'NEW234' }, { merge: true });
  b.set(doc(d, 'invites', 'NEW234'), { tutorUid: TUTOR, studentKey: KEY, claimedBy: null });
  await assertSucceeds(b.commit());
  const old = await getDoc(doc(db(PARENT), 'invites', CODE));
  if (old.exists()) throw new Error('old code should be gone');
  await assertSucceeds(claim(PARENT, 'NEW234'));
});

test('an invite cannot point at another tutor\'s record', async () => {
  await tutorInvites();
  await assertFails(setDoc(doc(db(OTHER_TUTOR), 'invites', 'XYZ234'), { tutorUid: OTHER_TUTOR, studentKey: KEY, claimedBy: null }));
  await assertFails(setDoc(doc(db(OTHER_TUTOR), 'invites', 'XYZ234'), { tutorUid: TUTOR, studentKey: KEY, claimedBy: null }));
});
