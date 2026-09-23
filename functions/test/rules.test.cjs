const {test,before,after}=require('node:test');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {doc,getDoc,setDoc,Timestamp}=require('firebase/firestore');
const {ref,uploadBytes,getBytes}=require('firebase/storage');
const fs=require('node:fs');
let env;
before(async()=>{
 env=await initializeTestEnvironment({projectId:'demo-acn-planner',firestore:{rules:fs.readFileSync('../firestore.rules','utf8')},storage:{rules:fs.readFileSync('../storage.rules','utf8')}});
 await env.withSecurityRulesDisabled(async ctx=>{
  const db=ctx.firestore();
  await setDoc(doc(db,'groups/class-a/members/alice'),{status:'approved',role:'member'});
  await setDoc(doc(db,'groups/class-a/members/bob'),{status:'pending',role:'member'});
  await setDoc(doc(db,'groups/class-a/messages/hello'),{text:'Private class discussion'});
  await setDoc(doc(db,'users/alice/records/syllabus'),{title:'Private syllabus'});
  for(const [id,groupId] of [['group-file','class-a'],['private-file','']])await setDoc(doc(db,'uploadTickets/'+id),{owner:'alice',groupId,size:3,mime:'text/plain',expiresAt:Timestamp.fromMillis(Date.now()+60000)});
 });
});
test('files require reservations and approved membership; private files stay private',async()=>{
 const alice=env.authenticatedContext('alice',{email_verified:true}).storage();
 const bob=env.authenticatedContext('bob',{email_verified:true}).storage();
 await assertSucceeds(uploadBytes(ref(alice,'groups/class-a/uploads/alice/group-file'),new Uint8Array([1,2,3]),{contentType:'text/plain'}));
 await assertFails(getBytes(ref(bob,'groups/class-a/uploads/alice/group-file')));
 await assertSucceeds(getBytes(ref(alice,'groups/class-a/uploads/alice/group-file')));
 await assertFails(uploadBytes(ref(alice,'groups/class-a/uploads/alice/no-ticket'),new Uint8Array([1,2,3]),{contentType:'text/plain'}));
 await assertSucceeds(uploadBytes(ref(alice,'users/alice/uploads/alice/private-file'),new Uint8Array([1,2,3]),{contentType:'text/plain'}));
 await assertFails(getBytes(ref(bob,'users/alice/uploads/alice/private-file')));
 await env.withSecurityRulesDisabled(async ctx=>setDoc(doc(ctx.firestore(),'groups/class-a/members/alice'),{status:'removed',role:'member'}));
 await assertFails(getBytes(ref(alice,'groups/class-a/uploads/alice/group-file')));
 await env.withSecurityRulesDisabled(async ctx=>setDoc(doc(ctx.firestore(),'groups/class-a/members/alice'),{status:'approved',role:'member'}));
});
after(async()=>{await env?.cleanup();});
test('approved student reads class, pending student cannot',async()=>{
 await assertSucceeds(getDoc(doc(env.authenticatedContext('alice',{email_verified:true}).firestore(),'groups/class-a/messages/hello')));
 await assertFails(getDoc(doc(env.authenticatedContext('bob',{email_verified:true}).firestore(),'groups/class-a/messages/hello')));
});
test('other class and private imports remain isolated',async()=>{
 await assertFails(getDoc(doc(env.authenticatedContext('charlie',{email_verified:true}).firestore(),'groups/class-a/messages/hello')));
 await assertFails(getDoc(doc(env.authenticatedContext('bob',{email_verified:true}).firestore(),'users/alice/records/syllabus')));
});
test('client cannot forge publisher or academic records',async()=>{
 const db=env.authenticatedContext('alice',{email_verified:true}).firestore();
 await assertFails(setDoc(doc(db,'groups/class-a/members/alice'),{role:'owner',status:'approved'}));
 await assertFails(setDoc(doc(db,'groups/class-a/records/quiz'),{title:'Forged quiz'}));
 await assertFails(setDoc(doc(db,'class_sessions/global'),{title:'Legacy write'}));
});
