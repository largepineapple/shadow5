import {expect,it,vi} from "vitest";
import {api,ApiError} from "./api";
it("retains server validation details and request identifier",async()=>{vi.stubGlobal("fetch",vi.fn().mockResolvedValue({ok:false,status:400,json:async()=>({code:"HTTP_400",message:"参数错误",fieldErrors:{delayMs:"需为整数"},requestId:"trace"})}));await expect(api("/tasks")).rejects.toMatchObject({name:"ApiError",status:400,fieldErrors:{delayMs:"需为整数"},requestId:"trace"});});
it("handles non-JSON and null error responses",async()=>{vi.stubGlobal("fetch",vi.fn().mockResolvedValue({ok:false,status:503,json:async()=>null}));await expect(api("/tasks")).rejects.toBeInstanceOf(ApiError);await expect(api("/tasks")).rejects.toThrow("503");});
