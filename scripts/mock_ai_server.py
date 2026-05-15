"""Lightweight mock AI service for smoke testing the Spring Boot backend.
Returns the same JSON structure as the real FastAPI AI service.
"""
import json
import os
import random
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
AI_SERVICE_DIR = os.path.join(PROJECT_ROOT, 'ai-service', 'app', 'services', 'mock_data')

# Load mock data once
def load_json(filename):
    path = os.path.join(AI_SERVICE_DIR, filename)
    with open(path, encoding='utf-8') as f:
        return json.load(f)

JOB_GRAPH = None
COMPANY_DATA = None
RESUME_RESPONSES = None
RESUME_SCORES = None

def ensure_loaded():
    global JOB_GRAPH, COMPANY_DATA, RESUME_RESPONSES, RESUME_SCORES
    if JOB_GRAPH is None:
        JOB_GRAPH = load_json('job_knowledge_graph.json')
    if COMPANY_DATA is None:
        COMPANY_DATA = load_json('company_data.json')
    if RESUME_RESPONSES is None:
        RESUME_RESPONSES = load_json('resume_responses.json')
    if RESUME_SCORES is None:
        RESUME_SCORES = load_json('resume_scores.json')


class MockAIHandler(BaseHTTPRequestHandler):
    timeout = 30

    def log_message(self, format, *args):
        pass  # suppress request logging

    def _send_json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        self.wfile.flush()

    def do_GET(self):
        ensure_loaded()
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)

        if path == '/health':
            self._send_json({"status": "UP", "service": "mock-ai-service"})

        elif path == '/jobs/categories':
            categories = sorted({e['category'] for e in JOB_GRAPH})
            self._send_json({"status": "success", "data": categories})

        elif path.startswith('/jobs/categories/') and path.endswith('/majors'):
            cat = path.split('/')[3]
            majors = [{"major": e['major'], "category": e['category']}
                      for e in JOB_GRAPH if e['category'] == cat]
            self._send_json({"status": "success", "data": majors})

        elif path.startswith('/jobs/majors/'):
            major = path.split('/jobs/majors/')[1]
            detail = next((e for e in JOB_GRAPH if e['major'] == major), None)
            self._send_json({"status": "success", "data": detail})

        elif path == '/jobs/search':
            q = qs.get('q', [''])[0][:200]
            results = []
            if q:
                for entry in JOB_GRAPH:
                    for key in ('primary_jobs', 'extended_jobs'):
                        for job in entry[key]:
                            searchable = ' '.join([
                                job['job_title'],
                                ' '.join(job['tags']),
                                job.get('description', ''),
                            ])
                            if q in searchable:
                                results.append({
                                    "job_title": job['job_title'],
                                    "major": entry['major'],
                                    "category": entry['category'],
                                    "tags": job['tags'],
                                    "description": job.get('description', ''),
                                    "confidence": round(random.uniform(0.6, 0.99), 2),
                                })
            self._send_json({"status": "success", "data": results[:5]})

        elif path == '/companies/search':
            q = qs.get('q', [''])[0]
            results = []
            if q:
                ql = q.strip().lower()
                for c in COMPANY_DATA:
                    if ql in c['name'].lower() or ql in c['name_en'].lower():
                        results.append({
                            "name": c['name'],
                            "name_en": c['name_en'],
                            "industry": c['basic_info']['industry'],
                        })
            self._send_json({"status": "success", "data": results})

        elif path.startswith('/companies/'):
            from urllib.parse import unquote
            name = unquote(path.split('/companies/')[1])
            company = next(
                (c for c in COMPANY_DATA
                 if c['name'].lower() == name.lower() or c['name_en'].lower() == name.lower()),
                None
            )
            if company:
                self._send_json({"status": "success", "data": company})
            else:
                self._send_json({"status": "success", "data": None, "message": "未找到该公司信息"})

        else:
            self._send_json({"status": "error", "message": "Not found"}, 404)

    def do_POST(self):
        ensure_loaded()
        parsed = urlparse(self.path)
        path = parsed.path

        if path == '/chat/send':
            content_length = int(self.headers.get('Content-Length', 0))
            if content_length > 0:
                self.rfile.read(content_length)
            self._send_json({
                "status": "success",
                "data": {
                    "response": "[Mock AI] 消息已收到",
                    "intent": "general",
                    "model": "mock-ai-service",
                }
            })

        elif path == '/resume/upload':
            # Read and discard the multipart body
            content_length = int(self.headers.get('Content-Length', 0))
            remaining = content_length
            while remaining > 0:
                chunk = self.rfile.read(min(remaining, 8192))
                if not chunk:
                    break
                remaining -= len(chunk)
            self._send_json(RESUME_RESPONSES['success'])

        elif path == '/resume/analyze':
            self._send_json(RESUME_SCORES['success'])

        else:
            self._send_json({"status": "error", "message": "Not found"}, 404)


if __name__ == '__main__':
    ensure_loaded()
    cats = sorted({e['category'] for e in JOB_GRAPH})
    print(f'Loaded {len(JOB_GRAPH)} entries, {len(cats)} categories from {AI_SERVICE_DIR}')
    port = int(os.environ.get('MOCK_AI_PORT', '8000'))
    server = HTTPServer(('0.0.0.0', port), MockAIHandler)
    print(f'Mock AI service running on port {port}')
    server.serve_forever()
