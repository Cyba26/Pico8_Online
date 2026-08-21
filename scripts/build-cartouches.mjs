/**
 * Génère `public/cartouches.json` à partir de l'arborescence `public/cartouches/`.
 *
 * Le fichier `.p8.png` d'une cartouche est aussi son image d'affichage : PICO-8
 * range le code dans les bits de poids faible d'un label 160×205, qui se dessine
 * tel quel. Pas de vignette séparée à produire — et surtout, ne jamais ré-encoder
 * le fichier, ça détruirait le code.
 *
 * Lancer : `node scripts/build-cartouches.mjs` (fait aussi par `npm run build`).
 */

import { readdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

/**
 * Les catégories, dans l'ordre d'affichage. Pour en ajouter une : créer le
 * dossier sous `public/cartouches/`, ajouter une ligne ici.
 */
const CATEGORIES = [
  { dossier: 'mes-cartouches', label: 'Mes cartouches' },
  { dossier: 'autres-cartouches', label: 'Autres cartouches' },
];

const RACINE = 'public/cartouches';

/** `03-shumpy_jump.p8.png` → `shumpy_jump`. Le préfixe ne sert qu'à ordonner. */
function nomAffiche(fichier) {
  return fichier.replace(/^\d+[-_]/, '').replace(/\.p8\.png$/i, '');
}

async function listerDossier({ dossier, label }) {
  let fichiers;
  try {
    fichiers = await readdir(join(RACINE, dossier));
  } catch {
    console.warn(`  ⚠ dossier absent, ignoré : ${dossier}`);
    return null;
  }

  // Tri par nom : le préfixe numérique optionnel donne l'ordre voulu, et à
  // défaut l'alphabétique reste stable d'un build à l'autre.
  const cartouches = fichiers
    .filter((f) => f.toLowerCase().endsWith('.p8.png'))
    .sort((a, b) => a.localeCompare(b, 'fr', { numeric: true }))
    .map((fichier) => ({
      nom: nomAffiche(fichier),
      url: `/cartouches/${dossier}/${encodeURIComponent(fichier)}`,
    }));

  console.log(`  ${label} — ${cartouches.length} cartouche(s)`);
  return { label, dossier, cartouches };
}

const groupes = (await Promise.all(CATEGORIES.map(listerDossier))).filter(Boolean);
const total = groupes.reduce((n, g) => n + g.cartouches.length, 0);

await writeFile(
  'public/cartouches.json',
  JSON.stringify({ genereLe: new Date().toISOString(), groupes }, null, 2) + '\n',
);

console.log(`✓ public/cartouches.json — ${total} cartouche(s), ${groupes.length} catégorie(s)`);
