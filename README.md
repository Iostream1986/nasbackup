# NasBackup

Android-Client, der Fotos und Videos automatisch per WebDAV auf einen eigenen
Server sichert. Ersatz fuer Google Fotos, ohne fremde Cloud.

## Was funktioniert

- Anmeldung direkt am WebDAV-Server (Server-URL, E-Mail, Passwort)
- Zugangsdaten verschluesselt im Android Keystore
- Automatischer Upload alle 6 Stunden, nur im WLAN, nur bei ausreichendem Akku
- Ablage nach Konto, Medien-Ordner, Geraet und Monat:
  `<E-Mail>/<Bucket>/<Geraet>/2026-09/IMG_1234.jpg`
- Nutzer-Isolation server-seitig: jedes Konto sieht per WebDAV ausschliesslich
  seinen eigenen Ordner (auch der eigene Root-Pfad ist gesperrt), siehe
  `server/serve.py` (`IsolatingHtpasswdDC`)
- Ueberspringt Dateien, die schon oben liegen
- Periodischer und manueller Sync schliessen sich gegenseitig aus (kein
  doppelter Lauf mehr, siehe ENTSCHEIDUNGEN.md)
- Ein einzelner Fehler stoppt den Lauf nicht
- Cloud-Galerie: geraeteuebergreifende Ansicht aller gesicherten Fotos/Videos
  vom Server, mit dauerhaftem Download einzelner Dateien in die Geraete-Galerie
- "Platz freigeben": loescht auf dem Geraet gezielt nur Originale, die laut
  Upload-Log schon auf dem Server liegen (mit doppelter Bestaetigung)

## Vor dem ersten Build anpassen

app/src/main/res/xml/network_security_config.xml

Dort steht die IP des Testservers (192.168.178.47). Ohne diesen Eintrag
blockiert Android unverschluesseltes HTTP. Bei anderer IP hier aendern.

Sobald der NAS per HTTPS erreichbar ist, kann die Datei geloescht und der
Verweis im Manifest entfernt werden.

## Bauen

    echo "sdk.dir=$HOME/Android/Sdk" > local.properties
    ./gradlew assembleDebug

APK danach unter:

    app/build/outputs/apk/debug/app-debug.apk

Installieren:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

## Bekannte Grenzen

- Kein echter bidirektionaler Sync: vom Server geladene oder dort geloeschte
  Dateien wandern nicht automatisch zurueck aufs bzw. vom Geraet. Bewusste
  Trennung in Backup / Cloud-Galerie / Platz freigeben, siehe ENTSCHEIDUNGEN.md.
- Das Upload-Log ist eine einfache Liste von IDs. Ab etwa 50.000 Dateien
  gehoert dort eine Room-Datenbank hin.
- Volle Ende-zu-Ende-Verschluesselung (auch vor dem Server-Admin selbst)
  ist bewusst nicht Teil des aktuellen Stands, aber als spaetere Option
  vorgesehen (relevant sobald Freunde/nicht-Familie mitnutzen).

  **Vermerk fuer spaeter:** Sobald E2E-Verschluesselung umgesetzt wird, soll
  sie so dokumentiert/aufgebaut sein, dass sich per KI (z.B. Code-Review durch
  ein Sprachmodell) moeglichst gut nachvollziehen laesst, *ob* und *wie* sie
  tatsaechlich greift -- also nachvollziehbare Implementierung statt nur
  Blackbox-Bibliothek, klare Kommentare/Doku zu Schluesselverwaltung und
  Verschluesselungsablauf, damit eine KI das im Nachhinein pruefen kann.
