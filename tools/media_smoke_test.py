"""Private-image authorization check. Creates one image/DM on a disposable demo stack."""
import argparse
import base64
import hashlib
import json
import urllib.error
import urllib.request
import uuid


def check(base):
    def call(path, token=None, data=None, raw=None, content_type="application/json"):
        headers = {"Content-Type": content_type, "Origin": base, "Accept-Language": "en"}
        if token:
            headers["Authorization"] = "Bearer " + token
        payload = raw if raw is not None else None if data is None else json.dumps(data).encode()
        request = urllib.request.Request(base + path, data=payload, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                body = response.read()
                return response.status, json.loads(body) if "json" in response.headers.get("Content-Type", "") else body
        except urllib.error.HTTPError as error:
            return error.code, error.read()

    def login(name):
        status, body = call("/api/auth/login", data={"usernameOrStudentNo": name, "password": "demo12345"})
        assert status == 200, f"Demo login failed: {status}"
        return body["data"]

    sender, recipient, stranger = [login(name) for name in ("linzhixia", "chenze", "xuan")]
    png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=")
    boundary = "campuspulse" + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="pixel.png"\r\n'
            'Content-Type: image/png\r\n\r\n').encode() + png + f"\r\n--{boundary}--\r\n".encode()
    status, uploaded = call("/api/upload/chat", sender["token"], raw=body,
                            content_type="multipart/form-data; boundary=" + boundary)
    assert status == 200, f"Image upload failed: {status}"
    url = uploaded["data"]["url"]
    message = {"contentType": "IMAGE", "imageUrl": url, "content": "Private image integration check",
               "clientMessageId": "media-" + uuid.uuid4().hex}
    endpoint = f'/api/dm/{recipient["user"]["id"]}/messages'
    status, sent = call(endpoint, sender["token"], data=message)
    assert status == 200, f"Image message failed: {status}"
    status, repeated = call(endpoint, sender["token"], data=message)
    assert status == 200 and repeated["data"]["id"] == sent["data"]["id"], "Retry created a duplicate"
    checks = {"sender": call(url, sender["token"])[0], "recipient": call(url, recipient["token"])[0],
              "stranger": call(url, stranger["token"])[0], "anonymous": call(url)[0],
              "public_path": call("/uploads/chat/" + url.rsplit("/", 1)[-1] + ".png")[0]}
    assert checks == {"sender": 200, "recipient": 200, "stranger": 403, "anonymous": 401, "public_path": 404}, checks
    image = call(url, recipient["token"])[1]
    assert image.startswith(b"\x89PNG\r\n\x1a\n"), "Response is not PNG"
    return {"private_image": checks, "idempotent_retry": "passed", "media_url": url,
            "sha256": hashlib.sha256(image).hexdigest()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8125")
    print(json.dumps(check(parser.parse_args().base_url.rstrip("/")), indent=2))
