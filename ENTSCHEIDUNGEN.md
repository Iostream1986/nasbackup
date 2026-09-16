# Getroffene Entscheidungen

- Kotlin + Compose nativ, weil WorkManager fuer zuverlaessigen
  Hintergrund-Upload alternativlos ist. iOS spaeter als eigenes Projekt.
- Kein eigenes Auth-Backend. Anmeldung geht direkt gegen WebDAV.
  Konten werden auf dem Server angelegt.
- Verteilung als APK ueber GitHub Releases + Obtainium, nicht ueber
  den Play Store.
- Testserver: wsgidav auf einem Kali-Mini-Server unter 192.168.178.47:8080.
  Spaeter Umzug auf echten NAS, dann zwingend TLS.
- Spendenfunktion kommt spaeter, als reiner Link. Ohne Store gilt
  keine Store-Richtlinie dafuer.
- Internet-Zugriff ueber Cloudflare Tunnel statt Portfreigabe: Der
  Internetanschluss ist DS-Lite (keine eigene oeffentliche IPv4), klassisches
  Port-Forwarding faellt damit weg. Cloudflare Tunnel loest gleichzeitig das
  Problem der wechselnden IP (kein DynDNS noetig) und liefert automatisches
  TLS ueber die eigene Domain `scheidl-nas.org`. Details siehe CLAUDE.md.
- Nutzer-Isolation server-seitig: Der Benutzername ist gleichzeitig der
  WebDAV-Ordnername (erster Pfadabschnitt der URL muss dem eingeloggten
  Konto entsprechen, durchgesetzt in `server/serve.py`). Bewusst kein
  separates Freigabe-Mapping pro Nutzer (wsgidav provider_mapping), damit
  neue Accounts ohne Server-Neustart und ohne manuellen Ordner-Schritt
  funktionieren. Schuetzt vor Zugriff anderer Nutzer, **nicht** vor dem
  Server-Admin selbst -- echte Ende-zu-Ende-Verschluesselung ist bewusst
  vertagt (siehe README, "Bekannte Grenzen"), aber als spaetere Option
  eingeplant, sobald auch Nicht-Familie (Freunde) mitnutzen soll.
- Kein echter bidirektionaler Sync (Geraet <-> Server bleiben nicht
  automatisch im gleichen Zustand). Stattdessen drei bewusst getrennte,
  manuelle Bausteine: Backup (Geraet -> Server, automatisch), Cloud-Galerie
  zum temporaeren Ansehen/Download einzelner Dateien vom Server, und
  "Platz freigeben" zum Loeschen bereits gesicherter Originale auf dem
  Geraet. Grund: wuerde der Server geloeschte Dateien automatisch wieder
  aufs Geraet schieben, waere das eigentliche Ziel von "Platz freigeben"
  (Speicherplatz sparen) direkt hinfaellig. Vorbild ist das Verhalten von
  Google Fotos, nicht ein Datei-Sync-Tool.
