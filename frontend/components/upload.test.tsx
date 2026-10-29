import React from "react";
import {beforeEach,expect,it,vi} from "vitest";
import {fireEvent,render,screen,waitFor,act} from "@testing-library/react";
import {Upload} from "./upload";
beforeEach(()=>{vi.stubGlobal("URL",Object.assign(URL,{createObjectURL:vi.fn(()=>"blob:test"),revokeObjectURL:vi.fn()}));});
function mount(options:{types?:string[];max?:number}={}){const onChange=vi.fn();render(<Upload index={0} acceptedMimeTypes={options.types||["image/png"]} maxFileSizeBytes={options.max||1024} onChange={onChange}/>);return {onChange,input:screen.getByLabelText("上传或替换图片 1")};}
it("uses template MIME and size limits before contacting server",()=>{const fetch=vi.fn();vi.stubGlobal("fetch",fetch);const {input}=mount({types:["image/png"],max:2});expect(input).toHaveAttribute("accept","image/png");fireEvent.change(input,{target:{files:[new File(["x"],"photo.jpg",{type:"image/jpeg"})]}});expect(screen.getByRole("alert")).toBeInTheDocument();fireEvent.change(input,{target:{files:[new File(["abc"],"photo.png",{type:"image/png"})]}});expect(fetch).not.toHaveBeenCalled();});
it("replacing an upload cancels old request and ignores its late success",async()=>{
 const pending:{resolve:(value:unknown)=>void;signal:AbortSignal}[]=[];
 vi.stubGlobal("fetch",vi.fn((_url:string,options:RequestInit)=>new Promise(resolve=>pending.push({resolve,signal:options.signal as AbortSignal}))));
 const {input,onChange}=mount();
 fireEvent.change(input,{target:{files:[new File(["one"],"one.png",{type:"image/png"})]}});
 expect(screen.getByText("正在上传…")).toBeInTheDocument();
 fireEvent.change(input,{target:{files:[new File(["two"],"two.png",{type:"image/png"})]}});
 expect(pending[0].signal.aborted).toBe(true);
 await act(async()=>{pending[1].resolve({ok:true,json:async()=>({id:"new",previewUrl:"/new"})});});
 await waitFor(()=>expect(onChange).toHaveBeenLastCalledWith({id:"new",previewUrl:"/new"}));
 await act(async()=>{pending[0].resolve({ok:true,json:async()=>({id:"old",previewUrl:"/old"})});});
 expect(onChange).toHaveBeenLastCalledWith({id:"new",previewUrl:"/new"});
 // Closing the picker without a selection preserves the prepared asset.
 const calls=onChange.mock.calls.length;fireEvent.change(input,{target:{files:[]}});expect(onChange).toHaveBeenCalledTimes(calls);
 fireEvent.click(screen.getByText("移除图片 1"));expect(onChange).toHaveBeenLastCalledWith(null);expect(URL.revokeObjectURL).toHaveBeenCalled();
});
it("server upload failure stays recoverable",async()=>{vi.stubGlobal("fetch",vi.fn().mockRejectedValue(new Error("offline")));const {input,onChange}=mount();fireEvent.change(input,{target:{files:[new File(["one"],"one.png",{type:"image/png"})]}});expect(await screen.findByRole("alert")).toHaveTextContent("offline");expect(onChange).toHaveBeenLastCalledWith(null);});
