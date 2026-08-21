# Archives

Données sorties du Postgres Railway avant la migration vers Vercel, conservées
au cas où on rebrancherait un jour ce qui a été retiré.

## `leaderboard-2026-08-20.json`

Les 24 scores du leaderboard, extraits le 20 août 2026 via `/api/leaderboard/:jeu`
juste avant l'arrêt du service Railway.

Tous portent sur `shumpy_jump`, entre le 14 juin et le 19 juillet 2026, par cinq
joueurs (Carzy, Cyba, Duncan, Quentin, Tang). Ils correspondent **exactement** au
tableau `SEED_SCORES` qui était codé en dur dans `src/server.ts` : aucun score
n'avait jamais été enregistré au-delà du seed initial.

Un enregistrement :

```json
{
  "id": "16cdd692-6b9b-47b8-9b98-c39aaff795cf",
  "game_name": "shumpy_jump",
  "player_name": "Cyba",
  "score": 14020,
  "Difficulty": "Easy",
  "session_id": null,
  "created_at": "2026-06-14T07:34:32.981Z"
}
```

### Pour le remettre en place

Il faudrait une base (Neon, par exemple) et les quatre routes qui allaient avec :
`GET /api/leaderboard/:jeu`, `POST /api/sessions`, `POST /api/scores`, plus la
suppression côté admin. La logique anti-triche vaut le coup d'être reprise telle
quelle : une session est ouverte au lancement de la partie, le jeton n'est
utilisable qu'une fois, et le score n'est accepté qu'après un délai minimum de
jeu. On la retrouve dans l'historique git, avant la migration.
