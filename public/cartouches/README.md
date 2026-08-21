# Cartouches

Chaque cartouche est **un seul fichier `.p8.png`**, déposé dans le dossier de sa
catégorie. Pas de vignette à fabriquer : le `.p8.png` est déjà l'image affichée.

```
public/cartouches/
├── mes-cartouches/        ← mises en avant, affichées en premier
│   ├── 01-shumpy_jump.p8.png
│   └── 02-shoot_the_sky.p8.png
└── autres-cartouches/
    └── 01-celeste.p8.png
```

## Ajouter une cartouche

1. Exporter depuis PICO-8 (`SAVE nom.p8.png`) ou récupérer le `.p8.png` du BBS.
2. Le déposer dans `mes-cartouches/` ou `autres-cartouches/`.
3. Committer, pousser. Vercel redéploie tout seul.

C'est tout — le manifeste `public/cartouches.json` est régénéré au build.

## Ordre d'affichage

Le préfixe `NN-` en début de nom donne l'ordre dans la catégorie ; il est retiré
du nom affiché. Sans préfixe, c'est l'ordre alphabétique. Les deux se mélangent
sans problème : il n'y a pas besoin de renuméroter tout un dossier pour insérer
une cartouche.

## Ajouter une catégorie

Créer le dossier ici, puis ajouter une ligne dans le tableau `CATEGORIES` de
[`scripts/build-cartouches.mjs`](../../scripts/build-cartouches.mjs). L'ordre du
tableau est l'ordre d'affichage des sections.

## Ne pas ré-encoder les fichiers

PICO-8 range le code de la cartouche dans les bits de poids faible d'un label
160×205. Ouvrir puis ré-enregistrer le PNG dans un éditeur d'images détruit ce
code, et la cartouche ne se lance plus alors que l'image a l'air intacte. Copier
le fichier tel quel, toujours.

## Le runtime

`pico8.dat` (l'export web de PICO-8) n'est pas — et ne sera pas — hébergé ici :
c'est un fichier sous licence Lexaloffle. Chaque visiteur dépose le sien, mis en
cache par le navigateur dans IndexedDB. C'était déjà le fonctionnement en prod
sur Railway.
