"""End-to-end smoke test runner for JobPilot backend."""
import json
import sys
import urllib.request
import urllib.parse
import time

BASE = 'http://localhost:8080/api/v1'
TOKEN = None
USER_ID = None
SESSION_ID = None
RESUME_ID = None
pass_count = 0
fail_count = 0
results = []


def api(method, path, body=None, auth=True, content_type='application/json'):
    global TOKEN
    url = f'{BASE}{path}'
    headers = {}
    if auth and TOKEN:
        headers['Authorization'] = f'Bearer {TOKEN}'
    data = None
    if body is not None:
        data = json.dumps(body).encode('utf-8')
        headers['Content-Type'] = f'{content_type}; charset=utf-8'
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(req)
        return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        body_text = e.read().decode('utf-8', errors='replace')
        print(f'  [HTTP {e.code}] {method} {path} -> {body_text[:200]}')
        try:
            return json.loads(body_text)
        except:
            return None
    except Exception as e:
        print(f'  [ERROR] {method} {path} -> {e}')
        return None


def assert_step(label, condition, detail=''):
    global pass_count, fail_count
    if condition:
        print(f'  [PASS] {label}')
        pass_count += 1
        results.append((label, 'PASS', ''))
    else:
        print(f'  [FAIL] {label}')
        if detail:
            print(f'         -> {detail}')
        fail_count += 1
        results.append((label, 'FAIL', detail))


def step(num, title):
    print()
    print('=' * 70)
    print(f'  STEP {num}: {title}')
    print('=' * 70)


# ── STEP 1: Register ──
step(1, 'POST /auth/register - Register new user')
reg_body = {
    'username': f'smoke_user_{int(time.time())}',
    'email': f'smoke_{int(time.time())}@test.com',
    'password': 'Smoke@12345',
    'major': '计算机科学与技术',
    'graduationYear': 2025
}
reg_resp = api('POST', '/auth/register', reg_body, auth=False)
assert_step('Register returns code=0', reg_resp and reg_resp.get('code') == 0,
            f'code={reg_resp.get("code") if reg_resp else "None"}')
if reg_resp and reg_resp['code'] == 0:
    TOKEN = reg_resp['data']['token']
    USER_ID = reg_resp['data']['user']['id']
    assert_step('Returns valid JWT token', TOKEN and len(TOKEN) > 20,
                f'token_len={len(TOKEN) if TOKEN else 0}')
    assert_step('Returns user info with id', USER_ID is not None,
                f'userId={USER_ID}')

# ── STEP 2: Login ──
step(2, 'POST /auth/login - Login and get JWT Token')
login_resp = api('POST', '/auth/login', {
    'email': reg_body['email'],
    'password': reg_body['password']
}, auth=False)
assert_step('Login returns code=0', login_resp and login_resp.get('code') == 0,
            f'code={login_resp.get("code") if login_resp else "None"}')
if login_resp and login_resp['code'] == 0:
    assert_step('Login returns JWT token', login_resp['data']['token'] and len(login_resp['data']['token']) > 20)
    TOKEN = login_resp['data']['token']

# ── STEP 3: Create Chat Session ──
step(3, 'POST /chat/sessions - Create chat session (Redis + MySQL dual-write)')
sess_resp = api('POST', '/chat/sessions')
assert_step('Create session returns code=0', sess_resp and sess_resp.get('code') == 0,
            f'code={sess_resp.get("code") if sess_resp else "None"}')
if sess_resp and sess_resp['code'] == 0:
    SESSION_ID = sess_resp['data']['sessionId']
    import re
    uuid_pattern = r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    assert_step('Session has UUID sessionId', bool(re.match(uuid_pattern, SESSION_ID)),
                f'sessionId={SESSION_ID}')
    assert_step('Session has database id', sess_resp['data']['id'] is not None and sess_resp['data']['id'] > 0,
                f'id={sess_resp["data"]["id"]}')

# ── STEP 4: GET /jobs/categories ──
step(4, 'GET /jobs/categories - Verify 21 professional categories')
cat_resp = api('GET', '/jobs/categories')
assert_step('Categories returns code=0', cat_resp and cat_resp.get('code') == 0,
            f'code={cat_resp.get("code") if cat_resp else "None"}')
if cat_resp and cat_resp['code'] == 0:
    cat_count = len(cat_resp['data'])
    assert_step(f'Returns exactly 21 categories (got {cat_count})', cat_count == 21,
                f'first 3: {cat_resp["data"][:3]}')

# ── STEP 5: GET /jobs/search?q=计算机相关工作 ──
step(5, 'GET /jobs/search?q=后端 - Verify fuzzy search')
search_resp = api('GET', '/jobs/search?q=' + urllib.parse.quote('后端'))
assert_step('Job search returns code=0', search_resp and search_resp.get('code') == 0,
            f'code={search_resp.get("code") if search_resp else "None"}')
if search_resp and search_resp['code'] == 0:
    result_count = len(search_resp['data'])
    assert_step(f'Search returns results (got {result_count})', result_count > 0,
                f'results={result_count}')
    if result_count > 0:
        first = search_resp['data'][0]
        assert_step('Result has job_title, tags, confidence',
                    'job_title' in first and 'tags' in first and 'confidence' in first,
                    f'first={first.get("job_title")}')

# ── STEP 6: GET /companies/字节跳动 ──
step(6, 'GET /companies/字节跳动 - Verify three-tier cache')
comp_resp = api('GET', '/companies/' + urllib.parse.quote('字节跳动'))
assert_step('Company intel returns code=0', comp_resp and comp_resp.get('code') == 0,
            f'code={comp_resp.get("code") if comp_resp else "None"}')
if comp_resp and comp_resp['code'] == 0:
    d = comp_resp['data']
    assert_step('Company name is 字节跳动', d.get('name') == '字节跳动',
                f'name={d.get("name")}')
    assert_step('Has basic_info with industry', d.get('basic_info', {}).get('industry') is not None,
                f'industry={d.get("basic_info", {}).get("industry")}')
    assert_step('Has salary_data', d.get('salary_data') is not None)
    assert_step('Has review_summary with dimensions',
                d.get('review_summary', {}).get('dimensions') is not None)

# Second request (Redis cache hit)
comp_resp2 = api('GET', '/companies/' + urllib.parse.quote('字节跳动'))
assert_step('Second request (Redis cache) returns same data',
            comp_resp2 and comp_resp2.get('code') == 0 and comp_resp2['data']['name'] == '字节跳动')

# ── STEP 7: POST /resumes/upload ──
step(7, 'POST /resumes/upload - Upload PDF and verify file path')
# Create a minimal PDF
pdf_content = b'%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n190\n%%EOF\n'

# Multipart upload
import io
boundary = '----SmokeTestBoundary123'
body_parts = []
body_parts.append(f'--{boundary}\r\n'.encode())
body_parts.append(b'Content-Disposition: form-data; name="file"; filename="smoke_test_resume.pdf"\r\n')
body_parts.append(b'Content-Type: application/pdf\r\n\r\n')
body_parts.append(pdf_content)
body_parts.append(f'\r\n--{boundary}--\r\n'.encode())
body = b''.join(body_parts)

headers = {
    'Authorization': f'Bearer {TOKEN}',
    'Content-Type': f'multipart/form-data; boundary={boundary}'
}
req = urllib.request.Request(f'{BASE}/resumes/upload', data=body, headers=headers, method='POST')
try:
    resp = urllib.request.urlopen(req)
    upload_resp = json.loads(resp.read())
except urllib.error.HTTPError as e:
    body_text = e.read().decode('utf-8', errors='replace')
    print(f'  [HTTP {e.code}] {body_text[:300]}')
    upload_resp = json.loads(body_text) if body_text else None

assert_step('Upload returns code=0', upload_resp and upload_resp.get('code') == 0,
            f'code={upload_resp.get("code") if upload_resp else "None"}')
if upload_resp and upload_resp['code'] == 0:
    RESUME_ID = upload_resp['data']['id']
    file_path = upload_resp['data'].get('rawFileUrl', '')
    assert_step('Resume has id', RESUME_ID is not None, f'id={RESUME_ID}')
    import re
    assert_step('File path matches /uploads/{userId}/... format',
                bool(re.match(r'^/uploads/\d+/\d{14}_smoke_test_resume\.pdf$', file_path)),
                f'path={file_path}')
    sd = upload_resp['data'].get('structuredData')
    assert_step('Resume has structuredData (AI parsed)', sd is not None)
    if sd:
        assert_step('structuredData has basic_info.name',
                    sd.get('basic_info', {}).get('name') is not None,
                    f'name={sd.get("basic_info", {}).get("name")}')

# ── STEP 8: GET /resumes/{id} ──
step(8, 'GET /resumes/{id} - Verify AI parsed resume with radar data')
if RESUME_ID:
    resume_resp = api('GET', f'/resumes/{RESUME_ID}')
    assert_step('Get resume returns code=0', resume_resp and resume_resp.get('code') == 0,
                f'code={resume_resp.get("code") if resume_resp else "None"}')
    if resume_resp and resume_resp['code'] == 0:
        sd = resume_resp['data'].get('structuredData')
        assert_step('Resume has structuredData', sd is not None)
        assert_step('structuredData has basic_info', sd and sd.get('basic_info') is not None)
        assert_step('structuredData has education array', sd and sd.get('education') and len(sd['education']) > 0)
        assert_step('structuredData has projects array', sd and sd.get('projects') and len(sd['projects']) > 0)
        assert_step('structuredData has skills array', sd and sd.get('skills') and len(sd['skills']) > 0)
        assert_step('structuredData has internships (radar chart data)', sd and sd.get('internships') is not None)
else:
    assert_step('Get resume (skipped)', False, 'No resume ID from Step 7')

# ── SUMMARY ──
print()
print('#' * 70)
print('  SMOKE TEST SUMMARY')
print('#' * 70)
print()
total = pass_count + fail_count
print(f'  Total: {total}  |  PASS: {pass_count}  |  FAIL: {fail_count}')
print()
for label, result, detail in results:
    marker = '[PASS]' if result == 'PASS' else '[FAIL]'
    print(f'  {marker} {label}')
    if detail:
        print(f'         -> {detail}')
print()
if fail_count == 0:
    print('  ALL SMOKE TESTS PASSED!')
else:
    print(f'  {fail_count} TEST(S) FAILED!')
print()
sys.exit(fail_count)
