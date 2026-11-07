# Site vitrine de Rago Client

Site statique (HTML, CSS, JS, sans build) prévu pour `client.rago-optimizer.com`, à la charte de
rago-optimizer.com : encre `#0A0A0B`, rouge `#FF3B30`, police Geist, logo R.

## Voir en local

```
cd site
python3 -m http.server 8000
```

puis http://localhost:8000.

## Mettre en ligne

Le dossier `site/` se publie tel quel. Sur Netlify : nouveau site depuis ce dépôt, *Base directory*
`site`, pas de commande de build (`netlify.toml` règle le cache des assets). Ajoute ensuite le
domaine `client.rago-optimizer.com` dans Netlify et un enregistrement DNS `CNAME client` vers
l'adresse `*.netlify.app` du site.

## À régler

- **Lien de téléchargement** : `DOWNLOAD_URL` en haut de `assets/js/main.js`. Vide, les boutons
  « Télécharger » mènent à la section Installer, dont le bouton ouvre le Discord.

## D'où viennent les visuels

- `assets/video/` : les fonds animés du client (`src/main/resources/assets/rago/video/*.bin`),
  convertis en MP4 H.264, avec leur première image en WebP.
- `assets/img/` : captures du dossier partagé du projet et textures du client (capes, boucliers),
  en WebP.
- `og-image.png` : image de partage 1200×630.
