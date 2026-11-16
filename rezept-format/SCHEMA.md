# Rezeptformat `kochbuch-rezept/1`

Die App speichert, importiert und exportiert Rezepte als JSON. Intern liegt jedes Rezept als genau so eine Datei vor, die Formate sind also identisch.

- **Maschinenlesbares Schema:** [`rezept.schema.json`](rezept.schema.json) (JSON Schema 2020-12)
- **Prompt für Claude:** [`claude-prompt.md`](claude-prompt.md)
- **Beispiele:** [`beispiele/`](beispiele)

## Dateien, die die App importiert

| Datei | Inhalt |
|---|---|
| `*.json` | ein Rezept-Objekt, ein Array von Rezepten oder `{ "recipes": [...] }` |
| `*.zip` (Rezept-Paket) | `rezepte.json` (ein Rezept oder Array) und optional `fotos/<id>.jpg` / `.jpeg` / `.png` / `.webp`, siehe unten |
| `*.zip` (Backup) | aus „Alle exportieren“: `rezepte/*.json`, `fotos/*` und `kueche.json` (Wochenplan, Einkaufsliste, Vorrat, Grundzutaten) |
| geteilter Text | JSON (z. B. aus der Claude-App) oder ein Link zu einer Rezeptseite |

Beim Import ist die App tolerant:
- Ein Markdown-Codezaun (` ```json `) um das JSON herum stört nicht.
- Unbekannte Felder werden ignoriert, Kommentare und nachgestellte Kommas sind erlaubt.
- `amount` darf auch ein String sein: `"0,5"`, `"1/2"`, `"½"`, `"1 ½"`, `"2-3"` (es zählt der erste Wert).
- Zutaten und Schritte dürfen reine Strings sein: `"ingredients": ["Salz"]`, `"steps": ["Alles mischen"]`.
- Fehlen `id` oder Zutaten-`key`s, erzeugt die App sie selbst.
- Pflicht sind nur `title` und mindestens eine Zutat oder ein Schritt.

Existiert ein Rezept mit derselben `id` schon, fragt die App: **Ersetzen**, **Als Kopie** oder **Überspringen**.

## Rezept-Pakete (.zip)

Mit einem Rezept-Paket kommen Rezepte und Fotos in einem Schritt in die App:

```
mein-paket.zip
├── rezepte.json          ← ein Rezept-Objekt oder ein Array (kochbuch-rezept/1)
└── fotos/
    ├── bauerntopf.jpg    ← Dateiname = id des Rezepts
    └── hack-pilz-pfanne.png
```

- Alles darf auch in **einem Oberordner** liegen (z. B. `mein-paket/rezepte.json`), wie beim Komprimieren eines Ordners. macOS-Beifang (`__MACOSX/`, `._*`, `.DS_Store`) wird ignoriert.
- **Fotos:** `jpg`, `jpeg`, `png` oder `webp`, je höchstens 20 MB. Der Dateiname ohne Endung muss der `id` eines Rezepts im selben Paket entsprechen; Groß- und Kleinschreibung spielt keine Rolle. Rezepte ohne `id` können kein Foto bekommen.
- **Ablauf:**
  - Erst werden die Rezepte importiert, genau wie beim JSON-Import. Gibt es eine `id` schon, fragt die App: *Ersetzen*, *Als Kopie* oder *Überspringen*.
  - Danach ordnet die App jedes Foto seinem Rezept zu. Bei „Als Kopie“ bekommt die Kopie das Foto.
  - Gespeichert wird genau wie über den Button **Foto**: verkleinert auf höchstens 1600 px und als JPEG.
- **Übersprungen** und am Ende aufgelistet werden:
  - Fotos ohne passendes Rezept
  - weitere Fotos für dasselbe Rezept (nur das erste zählt)
  - Fotos zu übersprungenen Rezepten
  - nicht unterstützte Dateien in `fotos/`
  - ungültige Rezepte
- **Zusammenfassung:** *X Rezepte importiert, Y Fotos zugeordnet, Z übersprungen*, darunter jeweils der Grund.
- **Öffnen** über ⋮ → Einstellungen → *Datei importieren*, aus dem Dateimanager mit *Öffnen mit* oder per *Teilen* an otai (`application/zip`).

Ein Beispiel ist [`beispiele/beispiele-mit-fotos.zip`](beispiele/beispiele-mit-fotos.zip).

## Felder

| Feld | Typ | Bedeutung |
|---|---|---|
| `format` | `"kochbuch-rezept/1"` | Formatversion |
| `id` | string | stabile ID (Slug) |
| `title`, `description` | string | |
| `servings` | int | Portionen, für die die Mengen gelten |
| `category` | string? | z. B. Hauptgericht, Eintopf |
| `tags` | string[] | erscheinen als Filter-Chips in der Übersicht |
| `difficulty` | string? | Einfach / Normal / Aufwendig |
| `times.totalMin/activeMin/cookMin` | int? | Minuten |
| `ingredients[]` | Objekt | `key`, `amount` (Zahl oder null = n. B.), `unit`, `name`, `note`, `group` |
| `steps[]` | Objekt | `text`, `ingredients` (Keys → Chips „Für diesen Schritt“), `timer {seconds, label}` |
| `tips[]` | Objekt | `title`, `text` |
| `nutrition` | Objekt? | pro Portion: `kcal`, `proteinG`, `fatG`, `carbsG` |
| `source` | Objekt? | `name`, `author`, `url` |

Diese Felder verwaltet die App selbst. Sie werden exportiert und beim Import eines Backups übernommen:

| Feld | Bedeutung |
|---|---|
| `favorite` | Herz in der Übersicht |
| `rating` | letzte Bewertung (1–5) |
| `cookedCount`, `lastCooked` | Kochzähler und Datum |
| `notes[]` | Rückmeldungen nach dem Kochen: `date`, `text`, `changes` (z. B. „Möhre(n): 2 → 3“), `rating` |
| `photo` | Dateiname des Fotos, nur im ZIP-Backup |
| `createdAt`, `updatedAt` | Zeitstempel |

## Rückmeldung nach dem Kochen

Am Ende der Kochansicht kannst du bewerten, Mengen ändern und eine Notiz schreiben. Geänderte Mengen werden auf die Basisportionen des Rezepts umgerechnet und **direkt ins Rezept übernommen**. Zusätzlich hält eine datierte Notiz jede Änderung fest, z. B. `Möhre(n): 2 → 3 (für 3 Portionen)`.

## Import von Webseiten

Die meisten Rezeptseiten, darunter Chefkoch, betten ihre Rezepte maschinenlesbar als [schema.org/Recipe](https://schema.org/Recipe) (JSON-LD) ein. Diese Daten liest die App über **⋮ → Einstellungen → Von Webseite importieren** oder über *Teilen → otai* aus dem Browser und übernimmt:

- Titel, Beschreibung, Portionen, Zeiten, Kategorie und Tags
- Zutaten, zerlegt in Menge, Einheit, Name und Hinweis (z. B. „2 Paprikaschote(n), bunte, ca. 250 g“)
- Zubereitungsschritte, Nährwerte, Autor:in und Quelle
- das Rezeptfoto

Was eine Webseite nicht liefert, ergänzt die App selbst: Timer aus Zeitangaben im Text („ca. 40 Min. köcheln“) und die Zuordnung der Zutaten zu den Schritten.

Blockiert eine Seite den direkten Abruf (Bot-Schutz), lädt die App sie wie ein Browser in einer unsichtbaren WebView.

Das Rezept-Paket [`beispiele/beispiele-mit-fotos.zip`](beispiele/beispiele-mit-fotos.zip) enthält beide Beispielrezepte samt den Fotos aus den PDFs.
