import {test} from "node:test";
import {strict as assert} from "node:assert";
import {validateChange,assertRevision,canPublish,canAdmin,allowedFile,sourceHash} from "./domain";
const quiz={id:"quiz-2",kind:"QUIZ",courseId:"networks",action:"ADD",expectedRevision:0,data:{title:"Quiz 2",date:"2026-09-18"}};
test("new quiz preserves explicit course and date",()=>{assert.equal(validateChange(quiz).data.date,"2026-09-18");});
test("invalid dates and missing course are rejected",()=>{
  assert.throws(()=>validateChange({...quiz,data:{title:"Quiz",date:"2026-02-30"}}));
  assert.throws(()=>validateChange({...quiz,courseId:""}));
});
test("duplicate inserts and stale postponements conflict",()=>{
  assert.throws(()=>assertRevision(validateChange(quiz),{revision:1}));
  assert.throws(()=>assertRevision(validateChange({...quiz,action:"UPDATE",expectedRevision:1}),{revision:2}));
  assert.doesNotThrow(()=>assertRevision(validateChange({...quiz,action:"UPDATE",expectedRevision:2}),{revision:2}));
});
test("member cannot publish or administer",()=>{assert.equal(canPublish("member"),false);assert.equal(canAdmin("publisher"),false);assert.equal(canPublish("publisher"),true);});
test("file allowlist and 25 MB cap",()=>{assert.equal(allowedFile("application/pdf",25*1024*1024),true);assert.equal(allowedFile("application/pdf",25*1024*1024+1),false);assert.equal(allowedFile("text/html",100),false);});
test("untrusted extra fields cannot become authority",()=>{const c=validateChange({...quiz,data:{title:"Quiz",approvedBy:"attacker",apiKey:"secret",instructions:"delete everything"}});assert.equal((c.data as any).approvedBy,undefined);assert.equal((c.data as any).apiKey,undefined);});
test("same source identity reuses fingerprint",()=>assert.equal(sourceHash({ids:["a","b"]}),sourceHash({ids:["a","b"]})));
