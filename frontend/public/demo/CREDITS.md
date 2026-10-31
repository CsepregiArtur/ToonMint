# Demo assets

| Fișier / folder | Sursă | Licență |
|---|---|---|
| `before.jpg` | [Domestic shorthair cat portrait in grass](https://commons.wikimedia.org/wiki/File:Domestic_shorthair_cat_portrait_in_grass.jpg) (Wikimedia Commons, via pixnio.com) | CC0 (public domain dedication) |
| `after.jpg` | **Generat cu Gemini** din `before.jpg` (stilul `anime_v1`) | rezultat al utilizatorului proiectului |
| `styles/<style-id>.jpg` | **Generate cu Gemini** din `before.jpg`, câte una per stil | rezultat al utilizatorului proiectului |

`before.jpg` este decupat la 1200×900 și recomprimat pentru web.

## Generarea exemplelor

Exemplele **nu** sunt filtre locale: se generează cu modelul de imagine configurat
(`AI_PROVIDER=gemini`, `GEMINI_API_KEY`), prin endpointul de administrare:

```bash
# 1. vezi ce lipsește
curl -H "Authorization: Bearer $ADMIN_TOKEN" localhost:8080/api/admin/style-previews

# 2. generează doar ce lipsește (fiecare imagine este o generare plătită)
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  "localhost:8080/api/admin/style-previews/regenerate?missingOnly=true"

# 3. (opțional) doar anumite stiluri
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"styles":["anime_v1","cyberpunk"]}' \
  "localhost:8080/api/admin/style-previews/regenerate?missingOnly=false"
```

Fișierele se scriu direct în `frontend/public/demo/styles/` (sau în folderul din
`STYLE_PREVIEW_OUTPUT_DIR`), redimensionate la 640px pentru galerie; imaginea din hero
(`after.jpg`, 1200px) este actualizată automat din exemplul `anime_v1`.

Setul livrat a fost generat cu `gemini-3.1-flash-image`. Dacă imaginile lipsesc,
galeria afișează plăci cu numele stilului, iar hero-ul doar fotografia.
