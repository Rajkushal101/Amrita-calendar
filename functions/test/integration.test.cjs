const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const {initializeApp,deleteApp}=require('firebase-admin/app');
const {getAuth}=require('firebase-admin/auth');
const {getFirestore}=require('firebase-admin/firestore');
const {randomUUID}=require('node:crypto');
let app,db,owner,student,outsider,group,course;
async function account(email){
 const u=await getAuth(app).createUser({email,password:'Pilot-test-7842!',emailVerified:true});
 const response=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password:'Pilot-test-7842!',returnSecureToken:true})});
 const body=await response.json();return {uid:u.uid,token:body.idToken};
}
async function call(who,name,data){
 const res=await fetch(`http://127.0.0.1:5001/demo-acn-planner/asia-south1/${name}`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${who.token}`},body:JSON.stringify({data})});
 const body=await res.json();if(body.error){const e=new Error(body.error.message);e.status=body.error.status;throw e;}return body.result;
}
before(async()=>{
 app=initializeApp({projectId:'demo-acn-planner'});db=getFirestore(app);
 owner=await account('owner@pilot.test');student=await account('student@pilot.test');outsider=await account('outsider@pilot.test');
 await db.doc('configuration/pilot').set({maxGroups:3,maxMembers:150,aiEnabled:false});
});
after(async()=>{await deleteApp(app);});
test('membership and academic publishing lifecycle',async()=>{
 group=randomUUID();const created=await call(owner,'createGroup',{id:group,name:'Networks class'});
 await call(student,'requestJoin',{code:created.code});
 await assert.rejects(call(student,'sendMessage',{groupId:group,id:randomUUID(),text:'Not yet allowed'}),e=>e.status==='PERMISSION_DENIED');
 await call(owner,'manageMember',{groupId:group,uid:student.uid,action:'approve'});
 const messageId=randomUUID();await call(student,'sendMessage',{groupId:group,id:messageId,text:'Quiz 2: Units 2 and 3'});
 await call(student,'sendMessage',{groupId:group,id:messageId,text:'Quiz 2: Units 2 and 3'});
 assert.equal((await db.collection(`groups/${group}/messages`).get()).size,1);
 course=group+'_'+randomUUID();const addCourse={id:course,kind:'COURSE',courseId:'',action:'ADD',expectedRevision:0,data:{title:'Networks',code:'23CYS201',semester:3}};
 await assert.rejects(call(student,'commitRecords',{groupId:group,operationId:randomUUID(),changes:[addCourse]}),e=>e.status==='PERMISSION_DENIED');
 await call(owner,'commitRecords',{groupId:group,operationId:randomUUID(),changes:[addCourse]});
 const quiz={id:group+'_'+randomUUID(),kind:'QUIZ',courseId:course,action:'ADD',expectedRevision:0,data:{title:'Quiz 2',date:'2026-09-18'}};
 const proposalId=randomUUID();await db.doc(`groups/${group}/proposals/${proposalId}`).set({owner:student.uid,status:'pending',revision:1,changes:[quiz],questions:[],sources:[{id:messageId,type:'message',revision:1}],sourceIds:[messageId]});
 await assert.rejects(call(student,'applyProposal',{groupId:group,proposalId,expectedRevision:1,operationId:randomUUID(),changes:[quiz]}),e=>e.status==='PERMISSION_DENIED');
 await call(owner,'applyProposal',{groupId:group,proposalId,expectedRevision:1,operationId:randomUUID(),changes:[quiz]});
 const postpone={...quiz,action:'UPDATE',expectedRevision:1,data:{title:'Quiz 2',date:'2026-09-21'}};
 await call(owner,'commitRecords',{groupId:group,operationId:randomUUID(),changes:[postpone]});
 await assert.rejects(call(owner,'commitRecords',{groupId:group,operationId:randomUUID(),changes:[postpone]}),e=>e.status==='ABORTED');
 assert.equal((await db.doc(`groups/${group}/records/${quiz.id}`).get()).get('data.date'),'2026-09-21');
 await assert.rejects(call(outsider,'sendMessage',{groupId:group,id:randomUUID(),text:'Cross-class access'}),e=>e.status==='PERMISSION_DENIED');
 await call(owner,'manageMember',{groupId:group,uid:student.uid,action:'remove'});
 await assert.rejects(call(student,'sendMessage',{groupId:group,id:randomUUID(),text:'Removed'}),e=>e.status==='PERMISSION_DENIED');
});
test('private imports stay private and questions block approval',async()=>{
 const courseId=randomUUID();await call(student,'commitRecords',{groupId:'',operationId:randomUUID(),changes:[{id:courseId,kind:'COURSE',courseId:'',action:'ADD',expectedRevision:0,data:{title:'Private AI',code:'CYS301',semester:3}}]});
 assert.equal((await db.doc(`groups/${group}/records/${courseId}`).get()).exists,false);
 const change={id:randomUUID(),kind:'QUIZ',courseId,action:'ADD',expectedRevision:0,data:{title:'Uncertain quiz'}};
 const proposalId=randomUUID();await db.doc(`users/${student.uid}/proposals/${proposalId}`).set({owner:student.uid,status:'pending',revision:1,changes:[change],questions:['Which Friday?'],sources:[]});
 await assert.rejects(call(student,'applyProposal',{groupId:'',proposalId,expectedRevision:1,operationId:randomUUID(),changes:[change]}),e=>e.status==='FAILED_PRECONDITION');
 assert.equal((await db.doc(`users/${student.uid}/records/${change.id}`).get()).exists,false);
 await assert.rejects(call(student,'savePersonal',{id:change.id,data:{earned:12,maximum:10}}),e=>e.status==='INVALID_ARGUMENT');
});
test('mapped syllabus coverage validates authorized topics and rejects on membership removal',async()=>{
 const sharedGroup=randomUUID();
 const created=await call(owner,'createGroup',{id:sharedGroup,name:'Shared Syllabus Class'});
 await call(student,'requestJoin',{code:created.code});
 await call(owner,'manageMember',{groupId:sharedGroup,uid:student.uid,action:'approve'});

 const sharedCourse=sharedGroup+'_'+randomUUID();
 const addSharedCourse={id:sharedCourse,kind:'COURSE',courseId:'',action:'ADD',expectedRevision:0,data:{title:'Distributed Systems',code:'23CYS401',semester:4}};
 const sharedSyllabus=sharedGroup+'_'+randomUUID();
 const topic1=randomUUID(), topic2=randomUUID();
 const addSharedSyllabus={id:sharedSyllabus,kind:'SYLLABUS',courseId:sharedCourse,action:'ADD',expectedRevision:0,data:{title:'Full syllabus',topics:[{id:topic1,unit:'Unit 1',title:'Consensus'},{id:topic2,unit:'Unit 2',title:'Raft'}]}};
 await call(owner,'commitRecords',{groupId:sharedGroup,operationId:randomUUID(),changes:[addSharedCourse,addSharedSyllabus]});

 // Form A: direct enrollment in shared course
 await call(student,'savePersonal',{id:sharedCourse,data:{mapping:'enrolled'}});
 const privateAssessmentA={id:randomUUID(),kind:'QUIZ',courseId:sharedCourse,action:'ADD',expectedRevision:0,data:{title:'Direct Enrolled Quiz',date:'2026-10-01',coverage:[topic1]}};
 const resA=await call(student,'commitRecords',{groupId:'',operationId:randomUUID(),changes:[privateAssessmentA]});
 assert.equal(resA.records.length,1);
 assert.equal(resA.records[0].id,privateAssessmentA.id);

 // Form B: shared course mapped to private course (exact savePersonal emitted by Groups screen)
 const privateCourse=randomUUID();
 const addPrivateCourse={id:privateCourse,kind:'COURSE',courseId:'',action:'ADD',expectedRevision:0,data:{title:'My Dist Sys',code:'23CYS401',semester:4}};
 await call(student,'commitRecords',{groupId:'',operationId:randomUUID(),changes:[addPrivateCourse]});
 await call(student,'savePersonal',{id:sharedCourse,data:{mapping:privateCourse}});
 const privateAssessmentB={id:randomUUID(),kind:'MIDTERM',courseId:privateCourse,action:'ADD',expectedRevision:0,data:{title:'Mapped Midterm',date:'2026-10-15',coverage:[topic1,topic2]}};
 const resB=await call(student,'commitRecords',{groupId:'',operationId:randomUUID(),changes:[privateAssessmentB]});
 assert.equal(resB.records.length,1);
 assert.equal(resB.records[0].id,privateAssessmentB.id);

 // Unknown topic rejected
 const fakeTopic=randomUUID();
 const invalidAssessment={id:randomUUID(),kind:'QUIZ',courseId:privateCourse,action:'ADD',expectedRevision:0,data:{title:'Invalid Quiz',date:'2026-10-20',coverage:[fakeTopic]}};
 await assert.rejects(call(student,'commitRecords',{groupId:'',operationId:randomUUID(),changes:[invalidAssessment]}),e=>e.status==='INVALID_ARGUMENT');

 // Remove membership and verify rejection
 await call(owner,'manageMember',{groupId:sharedGroup,uid:student.uid,action:'remove'});
 const assessmentAfterRemovalA={id:randomUUID(),kind:'QUIZ',courseId:sharedCourse,action:'ADD',expectedRevision:0,data:{title:'Removed Member Quiz',date:'2026-10-25',coverage:[topic1]}};
 await assert.rejects(call(student,'commitRecords',{groupId:'',operationId:randomUUID(),changes:[assessmentAfterRemovalA]}),e=>e.status==='INVALID_ARGUMENT');

 const assessmentAfterRemovalB={id:randomUUID(),kind:'QUIZ',courseId:privateCourse,action:'ADD',expectedRevision:0,data:{title:'Removed Member Mapped Quiz',date:'2026-10-26',coverage:[topic1]}};
 await assert.rejects(call(student,'commitRecords',{groupId:'',operationId:randomUUID(),changes:[assessmentAfterRemovalB]}),e=>e.status==='INVALID_ARGUMENT');
});

test('demonstrate two-account workflow: request -> approve -> update -> publish -> offline retry -> member removal', async () => {
  // Step 1: Account 1 (Owner) creates group and course
  const workflowGroup = randomUUID();
  const createdGroup = await call(owner, 'createGroup', { id: workflowGroup, name: 'CYS 2026 Core' });
  assert.ok(createdGroup.code, 'Group code generated');

  const sharedCourseId = workflowGroup + '_' + randomUUID();
  const addCourse = {
    id: sharedCourseId,
    kind: 'COURSE',
    courseId: '',
    action: 'ADD',
    expectedRevision: 0,
    data: { title: 'Design and Analysis of Algorithms', code: '23CYS202', semester: 3 }
  };
  await call(owner, 'commitRecords', { groupId: workflowGroup, operationId: randomUUID(), changes: [addCourse] });

  // Step 2: Account 2 (Student) requests membership using the 6-digit code
  await db.doc(`joinLimits/${student.uid}`).delete();
  await call(student, 'requestJoin', { code: createdGroup.code });
  // Verify Account 2 cannot publish before approval
  await assert.rejects(
    call(student, 'sendMessage', { groupId: workflowGroup, id: randomUUID(), text: 'Pre-approval hello' }),
    e => e.status === 'PERMISSION_DENIED'
  );

  // Step 3: Account 1 (Owner) approves Account 2
  await call(owner, 'manageMember', { groupId: workflowGroup, uid: student.uid, action: 'approve' });
  const memberDoc = await db.doc(`groups/${workflowGroup}/members/${student.uid}`).get();
  assert.equal(memberDoc.get('status'), 'approved');
  assert.equal(memberDoc.get('role'), 'member');

  // Step 4: Account 2 (Student) sends an academic update message and submits a proposal
  const updateMsgId = randomUUID();
  await call(student, 'sendMessage', {
    groupId: workflowGroup,
    id: updateMsgId,
    text: 'Algorithms Quiz 1 announced for 2026-10-05 in AB1-101'
  });
  const msgDoc = await db.doc(`groups/${workflowGroup}/messages/${updateMsgId}`).get();
  assert.equal(msgDoc.get('text'), 'Algorithms Quiz 1 announced for 2026-10-05 in AB1-101');

  const quizRecordId = workflowGroup + '_' + randomUUID();
  const quizChange = {
    id: quizRecordId,
    kind: 'QUIZ',
    courseId: sharedCourseId,
    action: 'ADD',
    expectedRevision: 0,
    data: { title: 'Algorithms Quiz 1', date: '2026-10-05', time: '10:00', endTime: '11:00' }
  };
  const proposalId = randomUUID();
  await db.doc(`groups/${workflowGroup}/proposals/${proposalId}`).set({
    owner: student.uid,
    status: 'pending',
    revision: 1,
    changes: [quizChange],
    questions: [],
    sources: [{ id: updateMsgId, type: 'message', revision: 1 }],
    sourceIds: [updateMsgId]
  });

  // Account 2 cannot unilaterally approve or apply proposal (requires publisher/owner)
  await assert.rejects(
    call(student, 'applyProposal', {
      groupId: workflowGroup,
      proposalId,
      expectedRevision: 1,
      operationId: randomUUID(),
      changes: [quizChange]
    }),
    e => e.status === 'PERMISSION_DENIED'
  );

  // Step 5: Account 1 (Owner / Publisher) reviews and applies proposal (publishes record)
  await call(owner, 'applyProposal', {
    groupId: workflowGroup,
    proposalId,
    expectedRevision: 1,
    operationId: randomUUID(),
    changes: [quizChange]
  });

  // Verify published record in Firestore
  const publishedQuiz = await db.doc(`groups/${workflowGroup}/records/${quizRecordId}`).get();
  assert.equal(publishedQuiz.exists, true);
  assert.equal(publishedQuiz.get('data.title'), 'Algorithms Quiz 1');
  assert.equal(publishedQuiz.get('data.date'), '2026-10-05');
  assert.equal(publishedQuiz.get('revision'), 1);

  // Step 6: Account 2 (Student) enrolls in course (mapping: enrolled) to see it in Subjects and Planner
  await call(student, 'savePersonal', { id: sharedCourseId, data: { mapping: 'enrolled' } });
  const personalPref = await db.doc(`users/${student.uid}/personal/${sharedCourseId}`).get();
  assert.equal(personalPref.get('mapping'), 'enrolled');

  // Step 7: Check offline retry with idempotent operation ID
  const offlineOpId = 'offline_op_' + randomUUID();
  const studentPrivateStudyId = randomUUID();
  const studySessionChange = {
    id: studentPrivateStudyId,
    kind: 'STUDY_SESSION',
    courseId: sharedCourseId,
    action: 'ADD',
    expectedRevision: 0,
    data: { title: 'Self Study: Divide & Conquer', date: '2026-10-04' }
  };
  // Simulated initial sync / reconnection commit
  const retryResult1 = await call(student, 'commitRecords', {
    groupId: '',
    operationId: offlineOpId,
    changes: [studySessionChange]
  });
  assert.equal(retryResult1.records.length, 1);
  assert.equal(retryResult1.records[0].id, studentPrivateStudyId);

  // Idempotent retry: Re-sending identical operationId (e.g. timeout or duplicate retry) returns recorded result
  const retryResult2 = await call(student, 'commitRecords', {
    groupId: '',
    operationId: offlineOpId,
    changes: [studySessionChange]
  });
  assert.equal(retryResult2.records.length, 1);
  assert.equal(retryResult2.records[0].id, studentPrivateStudyId);

  // Step 8: Check member removal
  await call(owner, 'manageMember', { groupId: workflowGroup, uid: student.uid, action: 'remove' });
  const removedMemberDoc = await db.doc(`groups/${workflowGroup}/members/${student.uid}`).get();
  assert.equal(removedMemberDoc.get('status'), 'removed');

  // Post-removal operations by Account 2 are rejected
  await assert.rejects(
    call(student, 'sendMessage', { groupId: workflowGroup, id: randomUUID(), text: 'Should be rejected' }),
    e => e.status === 'PERMISSION_DENIED'
  );
  await assert.rejects(
    call(student, 'commitRecords', {
      groupId: workflowGroup,
      operationId: randomUUID(),
      changes: [{
        id: workflowGroup + '_' + randomUUID(),
        kind: 'QUIZ',
        courseId: sharedCourseId,
        action: 'ADD',
        expectedRevision: 0,
        data: { title: 'Unauthorized Quiz', date: '2026-10-10' }
      }]
    }),
    e => e.status === 'PERMISSION_DENIED'
  );
});
