"""Google desktop OAuth: system browser, loopback callback, PKCE + verified ID token."""
import base64
import hashlib
import json
from pathlib import Path
import secrets
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlencode, urlsplit, parse_qs
from urllib.request import Request, urlopen
import webbrowser

CONFIG = Path(__file__).resolve().parent / "google_client_secret.json"


def sign_in(cancel=None):
    if not CONFIG.exists():
        raise ValueError("Google login needs your Desktop OAuth client. Download its JSON from Google Cloud, save it as google_client_secret.json beside main.py, and install requirements.txt. See README.md.")
    try:
        from google.oauth2 import id_token
        from google.auth.transport.requests import Request as GoogleRequest
    except ImportError:
        raise ValueError("Install Google login dependencies: python -m pip install -r requirements.txt") from None
    config = json.loads(CONFIG.read_text(encoding="utf-8")).get("installed", {})
    client_id = config.get("client_id")
    if not client_id or not client_id.endswith(".apps.googleusercontent.com"):
        raise ValueError("Use an OAuth client of type Desktop app, not Web application.")
    state, nonce, verifier = (secrets.token_urlsafe(48) for _ in range(3))
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    result = {}

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            parsed = urlsplit(self.path)
            query = parse_qs(parsed.query)
            if parsed.path != "/" or query.get("state", [""])[0] != state:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b"Invalid OAuth callback.")
                return
            result.update({key: values[0] for key, values in query.items()})
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"<html><body style='background:#10151e;color:#fff;font:20px system-ui;padding:60px'><h1>CTFLY</h1><p>Google response received. Return to the desktop app to finish signing in.</p></body></html>")

        def log_message(self, *args):
            pass

    with HTTPServer(("127.0.0.1", 0), Handler) as server:
        server.timeout = 0.5
        redirect = f"http://127.0.0.1:{server.server_port}/"
        url = "https://accounts.google.com/o/oauth2/v2/auth?" + urlencode({
            "client_id": client_id, "redirect_uri": redirect, "response_type": "code",
            "scope": "openid email profile", "state": state, "nonce": nonce,
            "code_challenge": challenge, "code_challenge_method": "S256", "prompt": "select_account"})
        if not webbrowser.open(url):
            raise ValueError("Could not open your browser. Check your default browser settings.")
        deadline = time.monotonic() + 180
        while not result and time.monotonic() < deadline:
            if cancel and cancel.is_set():
                raise ValueError("Google sign-in cancelled.")
            server.handle_request()
        if not result:
            raise ValueError("Google sign-in timed out. Try again.")
    if "error" in result or "code" not in result:
        raise ValueError("Google sign-in was cancelled or denied.")
    fields = {"client_id": client_id, "code": result["code"], "code_verifier": verifier,
              "redirect_uri": redirect, "grant_type": "authorization_code"}
    if config.get("client_secret"):
        fields["client_secret"] = config["client_secret"]
    try:
        request = Request("https://oauth2.googleapis.com/token", data=urlencode(fields).encode(), method="POST")
        with urlopen(request, timeout=20) as response:
            tokens = json.load(response)
        # Checks signature, audience, expiration and issuer; never trust decoded JWT data alone.
        claims = id_token.verify_oauth2_token(tokens["id_token"], GoogleRequest(), client_id)
    except Exception:
        raise ValueError("Google token verification failed. Check your internet and OAuth client configuration.") from None
    if not claims.get("email_verified") or claims.get("nonce") != nonce or not claims.get("sub"):
        raise ValueError("Google identity verification failed.")
    return {"sub": claims["sub"], "email": claims["email"], "name": claims.get("name", claims["email"])}
