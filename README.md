# Studio (Android)

Application Android qui installe des IA en local (chat, code, images) dans des « machines » isolées,
et qui écrit des logiciels complets que tu télécharges en .zip.

## Compiler l'APK avec GitHub
1. Envoie tout le contenu de ce dossier dans ton dépôt GitHub (y compris `.github/` et `app/fixed.keystore`).
2. Onglet **Actions** → workflow **Build APK** (≈ 20-40 min la 1re fois, ensuite beaucoup plus court grâce au cache des moteurs).
3. Télécharge l'artefact **Studio-apk**, décompresse, installe l'APK. Ne mets PAS `local.properties` sur GitHub.

## Fonctions (version 0.5)
- **Chat** : réponses en direct, modes Assistant / Code / Prompt image, réflexion repliable, copier / régénérer,
  conversations sauvegardées, fichiers joints.
- **Projet** : génération d'un logiciel complet vue en direct, relecture optionnelle, aperçu, export .zip, amélioration.
- **Images** : création et modification de photos (image à image), galerie, enregistrement dans Photos.
- **Modèles** : installés (vérifier / désinstaller / reprendre), catalogue avec vérification des liens,
  navigateur Hugging Face (recherche, tailles, compatibilité RAM), navigateur web intégré qui installe les .gguf.
- **Réglages** : thème clair / sombre / système, machines, réglages IA, instructions perso, jeton, analyse complète, notice.
- Logo et icône adaptative, mode sombre et clair.

## Limites à connaître
- ARM64 uniquement. 4 Go de RAM minimum, 6 Go+ conseillés.
- Génération d'images sur CPU : plusieurs minutes. Commence en 256×256.
- Une IA de téléphone reste bien moins puissante qu'une grande IA en ligne.
- Le code n'a pas pu être compilé sur l'environnement de développement : si le build GitHub échoue,
  envoie le journal d'erreur.
