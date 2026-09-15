# NasBackup

Android-Client, der Fotos und Videos automatisch per WebDAV auf einen eigenen
Server sichert. Ersatz fuer Google Fotos, ohne fremde Cloud.

## Was funktioniert

- Anmeldung direkt am WebDAV-Server (Server-URL, E-Mail, Passwort)
- Zugangsdaten verschluesselt im Android Keystore
- Automatischer Upload alle 6 Stunden, nur im WLAN, nur bei ausreichendem Akku
- Ablage nach Monat und Konto: `<E-Mail>/DCIM/2026-09/IMG_1234.jpg`
- Nutzer-Isolation server-seitig: jedes Konto sieht per WebDAV ausschliesslich
  seinen eigenen Ordner (auch der eigene Root-Pfad ist gesperrt), siehe
  `server/serve.py` (`IsolatingHtpasswdDC`)
- Ueberspringt Dateien, die schon oben liegen
- Ein einzelner Fehler stoppt den Lauf nicht

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

- Nur eine Richtung: Geraet zum Server. Kein Download, keine Galerie.
- Das Upload-Log ist eine einfache Liste von IDs. Ab etwa 50.000 Dateien
  gehoert dort eine Room-Datenbank hin.
- Dateinamen kollidieren, wenn zwei Geraete desselben Kontos im selben Monat
  eine Datei mit gleichem Namen haben. Loesung spaeter: Geraetenamen in
  den Pfad.
- Periodischer (6h-Takt) und manueller ("Jetzt sichern") Sync sind zwei
  unabhaengige WorkManager-Auftraege und koennen gleichzeitig laufen, falls
  sie sich zeitlich ueberschneiden -- unschaedlich (kein Datenverlust), aber
  verdoppelt unnoetig die Serveranfragen. Sollten sich gegenseitig
  ausschliessen; noch nicht behoben.
- Volle Ende-zu-Ende-Verschluesselung (auch vor dem Server-Admin selbst)
  ist bewusst nicht Teil des aktuellen Stands, aber als spaetere Option
  vorgesehen (relevant sobald Freunde/nicht-Familie mitnutzen).
