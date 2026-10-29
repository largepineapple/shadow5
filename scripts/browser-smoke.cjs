/* Real Chromium checks against the Compose site; installed only in the CI test runner. */
const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const path = require("node:path");
const fs = require("node:fs/promises");

async function main() {
  const output = path.resolve("artifacts/screenshots");
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: "reduce" });
  const page = await context.newPage();
  const errors=[];
  page.on("pageerror", error=>errors.push(error.message));
  try {
    await page.goto("http://localhost:3000");
    await page.locator(".template-card").first().waitFor();
    assert.equal(await page.locator(".template-card").count(), 6);
    await page.screenshot({path:path.join(output,"desktop-templates.png"),fullPage:true});
    await page.locator('a[href="/templates/coast"]').click();
    await page.getByLabel("上传或替换图片 1").setInputFiles(path.resolve("frontend/public/demo/sample.png"));
    const submit=page.getByRole("button",{name:"开始制作 ↗"});
    await submit.waitFor();
    await page.locator(".upload-ready").waitFor();
    await page.getByText("演示控制",{exact:true}).click();
    await page.getByLabel("每阶段耗时（毫秒）").fill("100");
    await submit.click();
    await page.locator(".task-card.succeeded .result-ready").first().waitFor({timeout:30000});
    await page.screenshot({path:path.join(output,"desktop-result.png"),fullPage:true});

    await page.getByLabel("失败阶段").selectOption("GENERATE");
    await page.getByLabel("每阶段耗时（毫秒）").fill("300");
    await submit.click();
    await page.reload();
    await page.locator(".task-card.failed").first().waitFor({timeout:30000});
    assert.match(await page.locator(".task-card.failed").first().innerText(),/模拟生成/);

    await page.getByLabel("上传或替换图片 1").setInputFiles(path.resolve("frontend/public/demo/sample.png"));
    await page.locator(".upload-ready").waitFor();
    await page.getByText("演示控制",{exact:true}).click();
    await page.getByLabel("失败阶段").selectOption("");
    await page.getByLabel("每阶段耗时（毫秒）").fill("500");
    await page.route("**/api/tasks/*",route=>route.abort());
    await submit.click();
    await page.getByRole("button",{name:"恢复查询"}).first().waitFor();
    assert.equal(await page.locator(".task-card.failed").count(),1);
    await page.unroute("**/api/tasks/*");
    await page.getByRole("button",{name:"恢复查询"}).first().click();
    await page.waitForFunction(()=>document.querySelectorAll(".task-card.succeeded").length===2,{},{timeout:30000});
    await page.screenshot({path:path.join(output,"desktop-history-states.png"),fullPage:true});
    assert.equal(await page.locator(".status").first().evaluate(el=>getComputedStyle(el).animationName),"none");

    await page.setViewportSize({width:375,height:812});
    await page.goto("http://localhost:3000/history");
    await page.locator(".task-card").first().waitFor();
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
    await page.screenshot({path:path.join(output,"mobile-history.png"),fullPage:true});
    const stranger=await browser.newContext();
    const other=await stranger.newPage();
    await other.goto("http://localhost:3000/history");
    await other.getByText("第一份作品，等你开始").waitFor();
    assert.equal(await other.locator(".task-card").count(),0);
    assert.deepEqual(errors,[]);
    await stranger.close();
    console.log("Browser checks passed: upload, successful preview, failure, refresh, query recovery, mobile layout, reduced motion, identity isolation");
  } catch(error) {
    await page.screenshot({path:path.join(output,"failure.png"),fullPage:true}).catch(()=>{});
    throw error;
  } finally {await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
