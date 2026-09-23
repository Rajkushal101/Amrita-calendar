import {z} from "zod";
import {createHash} from "node:crypto";

export const id = z.string().regex(/^[a-zA-Z0-9_-]{1,120}$/);
const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine(v => {
  const d = new Date(v + "T00:00:00Z"); return !isNaN(+d) && d.toISOString().slice(0,10) === v;
});
const time = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/);
export const kind = z.enum(["COURSE","SYLLABUS","MIDTERM","END_SEMESTER","QUIZ","ASSIGNMENT","PROJECT","RESOURCE","STUDY_SESSION","TIMETABLE","NOTICE"]);
export const dataSchema = z.object({
  title: z.string().trim().min(1).max(200), content: z.string().max(50000).default(""),
  code: z.string().max(40).optional(), semester: z.number().int().min(1).max(12).optional(),
  date: date.nullable().optional(), time: time.nullable().optional(), endTime: time.optional(),
  day: z.number().int().min(1).max(7).optional(), room: z.string().max(100).optional(),
  templateId: id.optional(), cancelled: z.boolean().default(false), isHoliday: z.boolean().default(false),
  reminderMinutes: z.number().int().min(0).max(10080).optional(),
  url: z.string().url().refine(v => v.startsWith("https://")).optional(),
  coverage: z.array(id).max(500).default([]), coverageNeedsReview: z.boolean().default(false),
  topics: z.array(z.object({id, unit: z.string().max(200), title: z.string().max(500)})).max(500).default([]),
  milestones: z.array(z.object({id, title: z.string().max(500), date: date.nullable().optional()})).max(100).default([]),
  attachmentIds: z.array(id).max(20).default([])
});
export const changeSchema = z.object({
  id, kind, courseId: z.string().max(120).default(""), action: z.enum(["ADD","UPDATE","DELETE"]),
  expectedRevision: z.number().int().min(0), data: dataSchema
});
export type Change = z.infer<typeof changeSchema>;
export function validateChange(raw: unknown): Change {
  const c = changeSchema.parse(raw);
  if(c.kind === "COURSE" && (!c.data.code || !c.data.semester)) throw new Error("Course code and semester are required");
  if(!["COURSE","NOTICE","STUDY_SESSION"].includes(c.kind) && !c.courseId) throw new Error("Choose a course");
  if(c.kind === "TIMETABLE" && (!c.data.day || !c.data.time || !c.data.endTime || c.data.endTime <= c.data.time)) throw new Error("Invalid class time");
  if(c.action === "ADD" && c.expectedRevision !== 0) throw new Error("New records must have revision zero");
  return c;
}
export function assertRevision(c: Change, existing?: {revision:number,deleted?:boolean}) {
  if(c.action === "ADD" ? !!existing : !existing || existing.deleted || existing.revision !== c.expectedRevision) throw new Error("CONFLICT: Record changed; review again");
}
export function sourceHash(value: unknown): string { return createHash("sha256").update(JSON.stringify(value)).digest("hex"); }
export function canPublish(role: string): boolean { return ["owner","administrator","publisher"].includes(role); }
export function canAdmin(role: string): boolean { return ["owner","administrator"].includes(role); }
export function allowedFile(type: string, bytes: number) {
  return ["application/pdf","image/jpeg","image/png","image/webp","text/plain"].includes(type) && bytes > 0 && bytes <= 25*1024*1024;
}
