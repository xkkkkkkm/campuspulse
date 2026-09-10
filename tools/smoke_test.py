"""Read-only smoke checks against a running demo, apart from issuing login tokens."""
import argparse
import json
import sys
import urllib.error
import urllib.request


def http_json(method, url, token=None, body=None):
    headers={"Content-Type":"application/json","Accept-Language":"en"}
    if token: headers["Authorization"]="Bearer "+token
    request=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(request,timeout=20) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as error:
        try:return error.code,json.load(error)
        except Exception:return error.code,{}


def checked(response):
    status,body=response
    if status!=200 or not body.get("success"):raise RuntimeError(f"Request failed: status={status}, code={body.get('code')}")
    return body["data"]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url",default="http://127.0.0.1:8125")
    base=parser.parse_args().base_url.rstrip("/")
    # The paged team lobby must remain available before login.
    teams=checked(http_json("GET",base+"/api/teams/page?page=1&size=5"))
    login=checked(http_json("POST",base+"/api/auth/login",body={"usernameOrStudentNo":"linzhixia","password":"demo12345"}))
    token=login["token"]
    me=checked(http_json("GET",base+"/api/auth/me",token))
    activities=checked(http_json("GET",base+"/api/activities?page=1&size=5",token))
    recommendations=checked(http_json("GET",base+"/api/recommendations/activities?size=5",token))
    forbidden=http_json("GET",base+"/api/admin/users",token)[0]
    if forbidden!=403:raise RuntimeError("Ordinary account unexpectedly accessed admin API")
    print(json.dumps({"user":me.get("username"),"activities":len(activities["items"]),"teams":len(teams["items"]),"recommendations":len(recommendations),"admin_access":forbidden},indent=2))

if __name__=="__main__":
    try:main()
    except Exception as error:print("Smoke test failed:",error,file=sys.stderr);sys.exit(1)
