"""Simple role-based auth for the management panel.

Two roles: ``admin`` (full control incl. bans) and ``moderator`` (kick, clear
VL, view). Credentials come from environment variables so nothing is hardcoded
in the repo. Sessions are in-memory bearer tokens.

Env:
    RXAC_ADMIN_USER / RXAC_ADMIN_PASSWORD   (default admin / admin)
    RXAC_MOD_USER   / RXAC_MOD_PASSWORD     (default mod / mod)
"""

from __future__ import annotations

import os
import secrets
import time
from typing import Dict, Optional, Tuple

_TOKEN_TTL = 60 * 60 * 12  # 12 hours


def _users() -> Dict[str, Tuple[str, str]]:
    return {
        os.environ.get("RXAC_ADMIN_USER", "admin"):
            (os.environ.get("RXAC_ADMIN_PASSWORD", "admin"), "admin"),
        os.environ.get("RXAC_MOD_USER", "mod"):
            (os.environ.get("RXAC_MOD_PASSWORD", "mod"), "moderator"),
    }


# token -> {"user", "role", "ts"}
_SESSIONS: Dict[str, dict] = {}


def login(username: str, password: str) -> Optional[Tuple[str, str]]:
    """Return (token, role) on success, else None."""
    rec = _users().get(username)
    if not rec or not secrets.compare_digest(rec[0], password or ""):
        return None
    token = secrets.token_urlsafe(24)
    _SESSIONS[token] = {"user": username, "role": rec[1], "ts": time.time()}
    return token, rec[1]


def session(token: Optional[str]) -> Optional[dict]:
    if not token:
        return None
    rec = _SESSIONS.get(token)
    if not rec:
        return None
    if time.time() - rec["ts"] > _TOKEN_TTL:
        _SESSIONS.pop(token, None)
        return None
    return rec


def logout(token: Optional[str]) -> None:
    if token:
        _SESSIONS.pop(token, None)
