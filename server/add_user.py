#!/usr/bin/env python3
"""
Legt einen neuen WebDAV-Account mit einem zufaelligen Einmal-Passwort an.

Nutzung (im Verzeichnis /home/markus/webdav):
    .venv/bin/python3 add_user.py person@example.de

Gibt Server-Adresse, E-Mail und das Einmal-Passwort aus -- zum Weitergeben
an die Person. Passwort danach selbst in der App aendern lassen
(server/serve.py -> /_password).
"""

import secrets
import string
import sys

from passlib.apache import HtpasswdFile

HTPASSWD_FILE = "users.htpasswd"
SERVER_URL = "https://nas.scheidl-nas.org"
PASSWORD_LENGTH = 16


def generate_password(length: int = PASSWORD_LENGTH) -> str:
    alphabet = string.ascii_letters + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def main() -> None:
    if len(sys.argv) != 2:
        print(f"Nutzung: {sys.argv[0]} <email>", file=sys.stderr)
        sys.exit(1)

    email = sys.argv[1]
    if "/" in email:
        # Der Benutzername ist gleichzeitig der isolierte Ordnername auf dem
        # Server (siehe serve.py, IsolatingHtpasswdDC) -- ein "/" wuerde die
        # Pfadstruktur durcheinanderbringen.
        print("E-Mail-Adresse darf kein '/' enthalten.", file=sys.stderr)
        sys.exit(1)

    ht = HtpasswdFile(HTPASSWD_FILE, default_scheme="bcrypt")
    if email in ht.users():
        print(f"Account {email} existiert bereits.", file=sys.stderr)
        sys.exit(1)

    password = generate_password()
    ht.set_password(email, password)
    ht.save()

    print("Account angelegt:")
    print(f"  Server:          {SERVER_URL}")
    print(f"  E-Mail:          {email}")
    print(f"  Einmal-Passwort: {password}")
    print()
    print("Passwort nach dem ersten Login in der App selbst aendern lassen.")


if __name__ == "__main__":
    main()
