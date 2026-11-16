# Prompt: Rezept ins otai-Format umwandeln

> **Tipp:** Für Rezepte von Webseiten wie Chefkoch brauchst du Claude nicht. Nutze in der App **⋮ → Einstellungen → Von Webseite importieren** oder teile den Link aus dem Browser an otai; Foto inklusive. Der Weg über Claude ist für PDFs, Kochbuchfotos und handschriftliche Rezepte gedacht.
>
> **Mit Fotos:** Kann Claude bei dir Dateien erstellen (Code-Ausführung bzw. Datei-Erstellung aktiviert), hänge den Zusatz **„Variante: Rezept-Paket“** am Ende an. Claude liefert dann eine ZIP mit `rezepte.json` und den Fotos aus dem PDF. Die ZIP importierst du über **⋮ → Einstellungen → Datei importieren** oder per *Teilen* an otai.
>
> **Ohne Fotos:** Ins reine JSON kann Claude keine Bilder einbetten. Steht im Rezept eine Quell-URL, lädt die App das Foto über **⋮ → Foto von Originalseite** nach. Sonst fügst du es über den Button **Foto** hinzu.

Kopiere alles unterhalb der Linie in einen Chat mit Claude und hänge das Rezept an (PDF, Foto, Link-Text oder abgetippt). Die Antwort teilst du direkt aus der Claude-App an **otai**. Alternativ speicherst du sie als `.json`-Datei (z. B. `bauerntopf.json`) und importierst sie über **⋮ → Einstellungen → Datei importieren** oder öffnest sie aus dem Dateimanager mit der App.

---

Wandle das angehängte Rezept in das JSON-Format **kochbuch-rezept/1** meiner Rezept-App um.

**Ausgabe:** Gib ausschließlich einen einzigen ```json-Codeblock aus, ohne Text davor oder danach. Bei mehreren Rezepten gibst du ein JSON-Array aus.

**Struktur:**

```json
{
  "format": "kochbuch-rezept/1",
  "id": "slug-des-titels",
  "title": "…",
  "description": "1–2 Sätze",
  "servings": 2,
  "category": "Hauptgericht",
  "tags": ["…"],
  "difficulty": "Einfach | Normal | Aufwendig",
  "times": { "totalMin": 30, "activeMin": 10, "cookMin": 20 },
  "ingredients": [
    { "key": "kartoffeln", "amount": 400, "unit": "g", "name": "Kartoffeln, festkochend", "note": null }
  ],
  "steps": [
    { "text": "…", "ingredients": ["kartoffeln"], "timer": { "seconds": 1200, "label": "Kartoffeln kochen" } }
  ],
  "tips": [ { "title": "…", "text": "…" } ],
  "nutrition": { "kcal": 571, "proteinG": 31.9, "fatG": 31.6, "carbsG": 38.9 },
  "source": { "name": "Chefkoch", "author": "…", "url": "…" }
}
```

**Regeln:**

1. **Sprache:** Deutsch. Fremdsprachige Rezepte übersetzt du. Japanische oder andere Fachbegriffe behältst du bei und erklärst sie beim ersten Vorkommen kurz, z. B. „Dashi (japanische Brühe)“. Offensichtliche Tippfehler korrigierst du. Inhalte erfindest du nicht.
2. **id:** Slug des Titels, also Kleinbuchstaben, ä→ae, ö→oe, ü→ue, ß→ss und Bindestriche statt Leerzeichen, z. B. `hack-pilz-pfanne`.
3. **servings:** Die Portionenzahl des Originals. Alle Mengen gelten für diese Zahl.
4. **Zutaten:**
   - `amount` ist eine Zahl mit Dezimalpunkt; Brüche rechnest du um (½ → 0.5).
   - „n. B.“, „etwas“ oder „nach Geschmack“ werden zu `"amount": null`.
   - `unit` ist eine von: g, kg, ml, l, EL, TL, Prise, Msp., Bund, Dose, Pck., Zehe(n), Scheibe(n), Becher, Tasse. Bei reinen Stückzahlen ist `unit` gleich `null`.
   - Zusätze wie „ca. 150 g“, „aus der Dose“, „gehäuft“ oder „z. B. instant“ kommen in `note`.
   - `key` ist ein kurzer, eindeutiger Slug pro Zutat.
   - Bei Unterrezepten (Teig, Sauce …) setzt du `group`, z. B. „Für die Sauce“.
5. **Schritte:**
   - Ein Schritt ist ein Arbeitsgang, wie im Original. Sehr lange Schritte darfst du sinnvoll teilen.
   - `ingredients` enthält die Keys der Zutaten, die in diesem Schritt verarbeitet oder hinzugefügt werden.
   - `timer` setzt du, wenn der Schritt eine Koch-, Back-, Ruhe- oder Wartezeit nennt („ca. 40 Min. köcheln“ → 2400 Sekunden). `label` umfasst 2–4 Wörter. Ohne Zeitangabe ist `timer` gleich `null`.
6. **times** in Minuten (Gesamt, Arbeitszeit, Koch-/Backzeit). Unbekannte Werte setzt du auf `null`.
7. **category** ist genau eine von: Hauptgericht, Beilage, Vorspeise, Suppe, Eintopf, Salat, Dessert, Backen, Frühstück, Snack, Getränk.
8. **tags:** 1–5 kurze deutsche Tags, groß geschrieben. Bevorzugt verwendest du: Vegetarisch, Vegan, Schnell (nur wenn gesamt ≤ 30 Min), Japanisch, Italienisch, Asiatisch, Hackfleisch, Geflügel, Fisch, Pilze, Deftig, One Pot, Meal Prep, Sonntagsessen.
9. **difficulty:** Einfach, Normal oder Aufwendig.
10. **tips:** Tipps, Varianten, Hinweise zur Aufbewahrung und Kniffe aus dem Original, je mit kurzem `title`.
11. **nutrition:** Nur wenn das Original Nährwerte pro Portion angibt, sonst `null`. Werte schätzt du nicht.
12. **source:** Quelle, Autor:in und URL, soweit bekannt. Druckansicht-Links wandelst du in die normale Rezept-URL um. Die URL ist wichtig, weil die App darüber das Rezeptfoto nachladen kann.
13. Felder wie `favorite`, `rating`, `notes` und `cookedCount` lässt du weg; die verwaltet die App.

---

### Zusatz: Variante: Rezept-Paket (nur mit Datei-Erstellung)

Erstelle statt des Codeblocks eine ZIP-Datei `rezepte.zip` mit diesem Aufbau:

- `rezepte.json`: das Rezept-Objekt bzw. bei mehreren Rezepten ein Array, exakt nach den Regeln oben. Jedes Rezept braucht eine `id`.
- `fotos/<id>.jpg`: das Hauptfoto des jeweiligen Rezepts aus der Vorlage (z. B. aus dem PDF extrahiert), Dateiname = `id` des Rezepts. Erlaubt sind jpg, jpeg, png oder webp. Logos, QR-Codes, Icons und Autorenbilder lässt du weg. Ohne passendes Foto entfällt die Datei.

Gib die ZIP als Download aus und nenne kurz, welche Rezepte und Fotos enthalten sind.
