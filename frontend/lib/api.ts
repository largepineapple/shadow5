export type Template = {
  id:string;title:string;category:string;description:string;coverUrl:string;
  requiredImageCount:number;acceptedMimeTypes:string[];maxFileSizeBytes:number;
};
export type Task = {
  id:string;templateId:string;status:"QUEUED"|"RUNNING"|"SUCCEEDED"|"FAILED";
  stage:string|null;failedStage:string|null;error:string|null;resultUrl:string|null;createdAt:string;
};
export type Asset={id:string;previewUrl:string};
export const stages:Record<string,string>={ASSET_CHECK:"素材检查",GENERATE:"模拟生成",FINALIZE:"结果整理"};
export const statuses:Record<string,string>={QUEUED:"等待处理",RUNNING:"制作中",SUCCEEDED:"制作完成",FAILED:"制作失败"};
export const active=(task?:Task)=>!!task&&(task.status==="QUEUED"||task.status==="RUNNING");
export class ApiError extends Error {
 constructor(message:string,public readonly status:number,public readonly code:string,public readonly fieldErrors:Record<string,string>,public readonly requestId?:string){super(message);this.name="ApiError";}
}
export async function api<T>(path:string,options:RequestInit={}):Promise<T>{
 const headers=new Headers(options.headers);
 if(options.body && !(options.body instanceof FormData) && !headers.has("Content-Type"))headers.set("Content-Type","application/json");
 const response=await fetch(`/api${path}`,{...options,credentials:"same-origin",headers});
 if(!response.ok){
  const raw:unknown=await response.json().catch(()=>null);
  const body=raw && typeof raw==="object"?raw as Record<string,unknown>:{};
  const fieldErrors:Record<string,string>={};
  if(body.fieldErrors && typeof body.fieldErrors==="object")for(const [key,value] of Object.entries(body.fieldErrors))if(typeof value==="string")fieldErrors[key]=value;
  throw new ApiError(typeof body.message==="string"?body.message:`请求失败 (${response.status})`,response.status,typeof body.code==="string"?body.code:`HTTP_${response.status}`,fieldErrors,typeof body.requestId==="string"?body.requestId:undefined);
 }
 return response.json();
}
