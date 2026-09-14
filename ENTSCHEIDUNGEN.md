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
