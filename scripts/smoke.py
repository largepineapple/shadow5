"""Compose smoke test; Python stdlib only. Run against a disposable demo instance."""
import base64
import http.cookiejar
import json
import os
import time
import urllib.request

BASE = os.environ.get("BASE_URL", "http://localhost:3000")

def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

def request(opener, path, body=None, content_type="application/json"):
    if isinstance(body, dict):
        body = json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=body, headers={"Content-Type": content_type})
    with opener.open(req, timeout=10) as response:
        data = response.read()
        return json.loads(data) if "json" in response.headers.get("Content-Type", "") else data

def main():
    owner, stranger = client(), client()
    request(owner, "/api/session", {})
    request(stranger, "/api/session", {})
    assert len(request(owner, "/api/templates")) == 6
    png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    boundary = "shadow-smoke-boundary"
    multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="sample.png"\r\nContent-Type: image/png\r\n\r\n'.encode() + png + f"\r\n--{boundary}--\r\n".encode())
    asset = request(owner, "/api/assets", multipart, f"multipart/form-data; boundary={boundary}")
    ids = []
    for stage in [None, "ASSET_CHECK", "GENERATE", "FINALIZE"]:
        task = request(owner, "/api/tasks", {"templateId": "coast", "assetIds": [asset["id"]], "failAt": stage, "delayMs": 100})
        ids.append((task["id"], stage))
    for task_id, failure in ids:
        for _ in range(120):
            result = request(owner, "/api/tasks/" + task_id)
            if result["task"]["status"] in ["FAILED", "SUCCEEDED"]:
                break
            time.sleep(0.25)
        assert result["task"]["status"] == ("FAILED" if failure else "SUCCEEDED"), result
        if not failure:
            assert request(owner, result["task"]["resultUrl"]).startswith(b"\x89PNG")
        else:
            assert result["task"]["failedStage"] == failure
    assert len(request(owner, "/api/tasks")) >= 4
    assert request(stranger, "/api/tasks") == []
    assert b"<html" in request(owner, "/")
    print("Compose smoke passed: templates, upload, four queued tasks, stage failures, result, identity, frontend")

if __name__ == "__main__":
    main()
