"""Stands in for OpenAI in the e2e smoke: every chat request gets the same
parsed receipt back, so a scan is deterministic, free, and needs no API key
(Dependabot pull requests cannot read repository secrets).

Answers both Chat Completions and Responses, whichever the api is set to.
Records nothing and never logs headers."""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# What the model "read". e2e/tests/smoke.spec.js asserts on these values.
RECEIPT = {
    "storeName": "E2E Маркет",
    "receiptDate": "23/09/2026",
    "items": [
        {"productName": "E2E Прясно мляко", "price": 2.49, "quantity": 1,
         "quantityUnit": "PIECE", "discountAmount": 0.0},
        {"productName": "E2E Банани", "price": 3.10, "quantity": 1.2,
         "quantityUnit": "KILOGRAM", "discountAmount": 0.5},
    ],
}


class Handler(BaseHTTPRequestHandler):
    def read_body(self):
        if self.headers.get("transfer-encoding", "").lower() == "chunked":
            body = b""
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip(), 16)
                if size == 0:
                    self.rfile.readline()
                    return body
                body += self.rfile.read(size)
                self.rfile.readline()
        return self.rfile.read(int(self.headers.get("content-length", 0)))

    def reply(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.reply(200, {"ok": True})

    def do_POST(self):
        self.read_body()
        text = json.dumps(RECEIPT, ensure_ascii=False)
        now = int(time.time())
        if "responses" in self.path:
            self.reply(200, {
                "id": "resp_e2e", "object": "response", "created_at": now,
                "status": "completed", "model": "gpt-6-luna",
                "output": [{"type": "message", "id": "msg_e2e", "role": "assistant",
                            "status": "completed",
                            "content": [{"type": "output_text", "text": text, "annotations": []}]}],
                "usage": {"input_tokens": 1, "output_tokens": 1, "total_tokens": 2},
            })
        else:
            self.reply(200, {
                "id": "chatcmpl-e2e", "object": "chat.completion", "created": now,
                "model": "gpt-6-luna",
                "choices": [{"index": 0, "finish_reason": "stop",
                             "message": {"role": "assistant", "content": text}}],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            })

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
