import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from threading import Thread
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src-tauri/python"))
from qwen_tts_bridge import download_file

PAYLOAD = b"model data" * 1024
DIGEST = hashlib.sha256(PAYLOAD).hexdigest()


class DownloadTests(unittest.TestCase):
    def setUp(self):
        folder = TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.dest = Path(folder.name) / "weights"
        self.part = self.dest.with_name("weights." + DIGEST + ".part")
        self.requests = []
        self.mode = "range"
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                header = self.headers.get("Range")
                owner.requests.append(header)
                start = int(header[6:-1]) if header and owner.mode == "range" else 0
                self.send_response(206 if start else 200)
                if start:
                    self.send_header("Content-Range", f"bytes {start}-{len(PAYLOAD)-1}/{len(PAYLOAD)}")
                self.end_headers()
                self.wfile.write(PAYLOAD[start:] if owner.mode != "corrupt" else b"x"*len(PAYLOAD))

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{server.server_port}/model"
        Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)

    def download(self):
        seen = []
        download_file(self.url, self.dest, len(PAYLOAD), DIGEST, seen.append)
        return seen

    def test_resume_then_reuse_without_http(self):
        self.part.write_bytes(PAYLOAD[:173])
        self.assertEqual(self.download()[0], 173)
        self.assertEqual(self.dest.read_bytes(), PAYLOAD)
        self.assertEqual(self.download(), [len(PAYLOAD)])
        self.assertEqual(self.requests, ["bytes=173-"])

    def test_server_ignoring_range_restarts_file(self):
        self.mode = "ignore"
        self.part.write_bytes(PAYLOAD[:173])
        self.assertEqual(self.download()[0], 0)
        self.assertEqual(self.dest.read_bytes(), PAYLOAD)

    def test_corrupt_bytes_never_promoted(self):
        self.mode = "corrupt"
        with self.assertRaisesRegex(IOError, "Checksum mismatch"):
            self.download()
        self.assertFalse(self.dest.exists())
        self.assertFalse(self.part.exists())
        self.mode = "range"
        self.download()
        self.assertEqual(self.dest.read_bytes(), PAYLOAD)


if __name__ == "__main__":
    unittest.main()
