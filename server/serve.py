#!/usr/bin/env python3
"""
Startet wsgidav wie gewohnt, haengt aber zusaetzlich einen winzigen
Passwort-Aendern-Endpunkt unter /_password in denselben Prozess/Port ein.

Warum kein eigener Port: Der Server haengt hinter einem DS-Lite-Anschluss,
da sind zusaetzliche Portfreigaben nicht ohne weiteres moeglich. Alles laeuft
deshalb ueber den ohnehin schon offenen WebDAV-Port.

Kein eigenes Nutzerkonto-System: Der Endpunkt aendert nur den Hash in
derselben htpasswd-Datei, die wsgidav sowieso schon zur Anmeldung nutzt,
und verlangt dafuer das aktuelle Passwort als Beleg. Passwort komplett
vergessen -> laeuft ueber den Admin per `htpasswd -Bb users.htpasswd ...`.

Start (ersetzt den bisherigen `wsgidav --config wsgidav.yaml`-Aufruf):
    .venv/bin/python3 serve.py
"""

import base64
import copy
import json
import os

import yaml
from passlib.apache import HtpasswdFile

from wsgidav import util
from wsgidav.dc.htpasswd_dc import HtpasswdDomainController
from wsgidav.default_conf import DEFAULT_CONFIG
from wsgidav.server.server_cli import _run_cheroot
from wsgidav.wsgidav_app import WsgiDAVApp

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(BASE_DIR, "wsgidav.yaml")
PASSWORD_PATH = "/_password"
MIN_PASSWORD_LEN = 8


class ReloadingHtpasswdDC(HtpasswdDomainController):
    """
    Der eingebaute HtpasswdDomainController laedt die Datei nur einmal beim
    Start (passlib's check_password() reloaded nicht selbst). Ohne das hier
    wuerde ein per /_password geaendertes Passwort erst nach einem Neustart
    des Servers greifen.
    """

    def basic_auth_user(self, realm, user_name, password, environ):
        self.htpasswd.load_if_changed()
        return super().basic_auth_user(realm, user_name, password, environ)


def build_config() -> dict:
    config = copy.deepcopy(DEFAULT_CONFIG)
    with open(CONFIG_FILE, encoding="utf-8") as f:
        file_opts = yaml.safe_load(f)
    util.deep_update(config, file_opts)
    # Immer unsere neu-ladende Variante nutzen, unabhaengig davon, was in
    # der YAML als domain_controller steht (die dient dort nur als Fallback-
    # Dokumentation, falls wsgidav mal ohne dieses Skript direkt gestartet wird).
    config["http_authenticator"]["domain_controller"] = ReloadingHtpasswdDC
    return config


def _htpasswd_path(config: dict) -> str:
    return util.get_dict_value(config, "htpasswd_dc", as_dict=True)["htpasswd_file"]


def _respond(start_response, status: str, text: str, extra_headers=None):
    headers = [("Content-Type", "text/plain; charset=utf-8")]
    headers.extend(extra_headers or [])
    start_response(status, headers)
    return [text.encode("utf-8")]


def make_change_password_app(htpasswd_file: str):
    def app(environ, start_response):
        if environ.get("REQUEST_METHOD") != "POST":
            return _respond(start_response, "405 Method Not Allowed", "POST erwartet", [("Allow", "POST")])

        auth_header = environ.get("HTTP_AUTHORIZATION", "")
        if not auth_header.startswith("Basic "):
            return _respond(
                start_response, "401 Unauthorized", "Anmeldung erforderlich",
                [("WWW-Authenticate", 'Basic realm="password"')],
            )

        try:
            user, old_password = base64.b64decode(auth_header[6:]).decode("utf-8").split(":", 1)
        except Exception:
            return _respond(start_response, "400 Bad Request", "Ungueltiger Authorization-Header")

        try:
            length = int(environ.get("CONTENT_LENGTH") or 0)
        except ValueError:
            length = 0
        body = environ["wsgi.input"].read(length) if length else b""

        try:
            data = json.loads(body or b"{}")
        except json.JSONDecodeError:
            return _respond(start_response, "400 Bad Request", "Ungueltiges JSON")

        new_password = data.get("new_password", "")
        if len(new_password) < MIN_PASSWORD_LEN:
            return _respond(
                start_response, "400 Bad Request",
                f"Neues Passwort muss mindestens {MIN_PASSWORD_LEN} Zeichen haben",
            )

        ht = HtpasswdFile(htpasswd_file, default_scheme="bcrypt")
        if not ht.check_password(user, old_password):
            return _respond(
                start_response, "401 Unauthorized", "Aktuelles Passwort falsch",
                [("WWW-Authenticate", 'Basic realm="password"')],
            )

        ht.set_password(user, new_password)
        ht.save()
        return _respond(start_response, "200 OK", "Passwort geaendert")

    return app


class Dispatch:
    """Reicht alles ausser PASSWORD_PATH unveraendert an wsgidav durch."""

    def __init__(self, dav_app, password_app):
        self.dav_app = dav_app
        self.password_app = password_app

    def __call__(self, environ, start_response):
        if environ.get("PATH_INFO") == PASSWORD_PATH:
            return self.password_app(environ, start_response)
        return self.dav_app(environ, start_response)


def main():
    config = build_config()
    dav_app = WsgiDAVApp(config)
    password_app = make_change_password_app(_htpasswd_path(config))
    app = Dispatch(dav_app, password_app)
    _run_cheroot(app, config, "cheroot")


if __name__ == "__main__":
    main()
