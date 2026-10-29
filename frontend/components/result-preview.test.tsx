import React from "react";
import {expect,it} from "vitest";
import {render,screen,fireEvent} from "@testing-library/react";
import {ResultPreview} from "./result-preview";
it("image loading errors do not change the successful task and support retry",()=>{render(<ResultPreview url="/api/assets/result/content"/>);expect(screen.getByRole("status")).toHaveTextContent("正在加载");fireEvent.error(screen.getByAltText(/制作结果/));expect(screen.getByRole("alert")).toHaveTextContent("制作已完成");fireEvent.click(screen.getByText("重新加载图片"));const image=screen.getByAltText(/制作结果/);expect(image).toHaveAttribute("src","/api/assets/result/content?retry=1");fireEvent.load(image);expect(screen.queryByRole("status")).not.toBeInTheDocument();});
