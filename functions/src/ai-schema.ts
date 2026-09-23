/** Provider output constraints complement (and never replace) domain validation. */
const text={type:"string"};
const date={type:["string","null"],description:"ISO calendar date YYYY-MM-DD; null when unannounced"};
const object=(properties:Record<string,unknown>,required:string[])=>({type:"object",properties,required,additionalProperties:false});
const array=(items:unknown,maxItems:number)=>({type:"array",items,maxItems});
const data=object({
  title:text,content:text,code:text,semester:{type:"integer",minimum:1,maximum:12},
  date,time:{type:["string","null"],description:"24-hour HH:mm; null when unannounced"},endTime:text,
  day:{type:"integer",minimum:1,maximum:7},room:text,url:text,
  reminderMinutes:{type:"integer",minimum:0,maximum:10080},templateId:text,
  cancelled:{type:"boolean"},isHoliday:{type:"boolean"},
  coverage:array(text,500),attachmentIds:array(text,20),
  topics:array(object({id:text,unit:text,title:text},["id","unit","title"]),500),
  milestones:array(object({id:text,title:text,date},["id","title"]),100)
},["title"]);
const change=object({
  id:text,kind:{type:"string",enum:["COURSE","SYLLABUS","MIDTERM","END_SEMESTER","QUIZ","ASSIGNMENT","PROJECT","RESOURCE","STUDY_SESSION","TIMETABLE","NOTICE"]},
  courseId:text,action:{type:"string",enum:["ADD","UPDATE","DELETE"]},expectedRevision:{type:"integer",minimum:0},data
},["id","kind","courseId","action","expectedRevision","data"]);
export const agentResponseSchema=object({answer:text,questions:array(text,30),changes:array(change,40)},["answer","questions","changes"]);
