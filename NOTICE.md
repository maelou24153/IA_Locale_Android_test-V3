# Notice de Studio

Tout fonctionne sur ton téléphone : tes discussions, ton code et tes images ne quittent jamais l'appareil. Internet sert seulement à télécharger les IA.

## Avant de commencer
- **Téléphone** : ARM64 (presque tous les récents), Android 8 ou plus. Android 10 ou plus pour enregistrer les images et les .zip dans le téléphone.
- **Mémoire** : 4 Go de RAM minimum, 6 Go ou plus conseillés. L'onglet Modèles affiche ta RAM et le type de modèle adapté.
- **Espace** : prévois 2 à 10 Go libres. Wi-Fi conseillé (de 0,4 à 5 Go par IA).
- L'écran reste allumé pendant qu'une IA travaille ou qu'un modèle se télécharge. Garde Studio ouvert au premier plan, sinon Android peut l'arrêter.
- Une machine nommée « principale » est créée toute seule : tu n'as rien à configurer pour commencer.

## Les 5 onglets
- **Chat** : discuter, programmer, préparer des prompts d'images.
- **Projet** : faire écrire un logiciel complet par l'IA.
- **Images** : créer ou modifier des images.
- **Modèles** : installer, vérifier et désinstaller les IA.
- **Réglages** : thème clair ou sombre, machines, réglages de l'IA, analyse complète, cette notice.

## Étape 1 : installer une IA (onglet Modèles)
- **Catalogue** : appuie sur **Installer**. La barre de progression s'affiche en haut, avec un bouton **Annuler**. Si le téléchargement s'interrompt, relance-le : il reprend là où il s'était arrêté.
- **Vérifier les liens** teste chaque lien du catalogue et affiche « lien valide » ou « lien cassé ».
- Pour discuter : Qwen2.5 1.5B ou 3B, Llama 3.2. Pour réfléchir : Qwen3, DeepSeek-R1. Pour le code : Qwen2.5-Coder (3B recommandé, 7B le plus fort avec 10 Go de RAM). Pour les images : SD-Turbo (rapide) ou Stable Diffusion 1.5 (qualité).
- Un avertissement rouge signale une IA trop lourde pour ta RAM.

## Étape 2 : trouver n'importe quelle autre IA
- **Hugging Face** : tape un nom (« qwen coder », « llama », « stable diffusion ») ou touche une idée. Les modèles les plus téléchargés s'affichent avec leurs téléchargements et leurs likes.
- Touche un modèle : la liste de ses fichiers apparaît avec leur taille. Un badge indique si le fichier est **adapté**, **lourd** ou **trop gros** pour ta RAM, et une étoile marque le fichier recommandé (en général Q4_K_M).
- Choisis « Images » en haut pour chercher des IA d'images.
- **Web** : un vrai navigateur intégré. Parcours Hugging Face, ouvre un modèle, onglet « Files », puis touche le bouton de téléchargement d'un fichier .gguf : Studio propose de l'installer et te demande si c'est une IA de chat/code ou d'images.
- Tu peux aussi coller un lien direct vers un fichier .gguf en bas de l'onglet Hugging Face.
- Pour un modèle réservé, ajoute ton jeton Hugging Face dans Réglages.

## Étape 3 : désinstaller et vérifier (onglet Installés)
- La liste montre chaque modèle, sa taille, la place utilisée et la place libre.
- **Désinstaller** efface le fichier du téléphone après confirmation.
- **Vérifier** contrôle qu'un fichier est complet et lisible (en-tête GGUF valide). **Tout vérifier** le fait pour tous.
- **Téléchargements interrompus** : les morceaux inachevés qui occupent de la place. Supprime-les ou relance l'installation pour les finir.

## Étape 4 : discuter
- Onglet **Chat**, choisis l'IA et le mode (**Assistant**, **Code**, **Prompt image**).
- La réponse s'écrit en direct, mot après mot. **Arrêter** la coupe quand tu veux.
- Les modèles à raisonnement affichent leur **réflexion** dans un bloc repliable : touche-le pour la lire.
- Sous chaque réponse : **Copier** et **Régénérer**. Le texte est sélectionnable.
- **Joindre un fichier** colle le contenu d'un fichier texte ou de code dans ton message.
- Tes conversations sont sauvegardées par machine et par mode : elles sont toujours là à la réouverture.
- Le premier message démarre le moteur (30 secondes à 2 minutes).

## Étape 5 : créer un logiciel complet (onglet Projet)
- Choisis un modèle Coder et décris ton logiciel avec précision. Exemple : « une calculatrice en page web avec historique des calculs et mode sombre ».
- Appuie sur **Générer le logiciel**. Tu vois en direct ce que l'IA écrit : d'abord le plan, puis chaque fichier, caractère par caractère, dans l'encadré **✍**.
- Compte de 5 à 30 minutes. **Arrêter** coupe la génération.
- Touche un fichier de la liste pour le lire et le copier.
- **Télécharger le .zip** : l'archive est dans Téléchargements, dossier Studio.
- **Relecture automatique** fait relire chaque fichier par l'IA : plus fiable, deux fois plus long.
- **Améliorer ce projet** : décris un changement, l'IA choisit les fichiers concernés et les réécrit sous tes yeux.
- Pour lancer un logiciel web, décompresse le .zip et ouvre index.html. Pour Python, lis le README du projet.
- Demande des logiciels simples : page web, script, petit jeu, calculatrice. Une IA de téléphone se perd sur les gros projets.
- Teste toujours le résultat. En cas d'erreur, utilise « Améliorer ce projet » en décrivant l'erreur.

## Étape 6 : créer une image
- Onglet **Images**, choisis le modèle, décris l'image **en anglais** (plus fiable). Exemple : « a cozy cabin in snowy mountains, warm light, detailed, digital painting ».
- Le mode **Prompt image** du Chat transforme ton idée en français en description anglaise prête à copier.
- Commence en **256×256** pour tester vite, puis passe à 512.
- Le journal sous le bouton montre l'avancement en direct.
- **Enregistrer** envoie l'image dans Photos, dossier Studio.

## Étape 7 : modifier une image
- Appuie sur **Choisir une photo à modifier** et prends une image de ta galerie.
- Décris le résultat voulu, par exemple « the same portrait as an oil painting ».
- Règle la **force de la modification** : bas = proche de l'original, haut = très différent. Essaie 50 à 70 %.
- Appuie sur **Transformer la photo**. Sur un résultat, **Modifier** le reprend comme nouvelle base.

## Réglages
- **Apparence** : Système, Clair ou Sombre.
- **Machines** : espaces séparés (IA, projets, images, conversations). Change de machine dans Modèles.
- **Créativité** : bas (0,2) = précis, idéal pour le code. Haut (0,9) = plus inventif.
- **Mémoire de conversation** : 2048 sur un petit téléphone, 8192 si tu as 10 Go de RAM ou plus.
- **Longueur des réponses** : monte à 2048 ou 4096 pour les modèles qui réfléchissent beaucoup.
- **Instructions personnalisées** : une consigne ajoutée à chaque discussion (« réponds brièvement »).
- **Threads CPU** : plus de threads = plus rapide mais le téléphone chauffe.
- Le chat et les images ne tournent pas en même temps : Studio arrête l'un pour libérer la mémoire de l'autre.

## Analyse complète
- **Analyser mon Studio** (Réglages) vérifie le téléphone, la RAM, l'espace libre, **essaie de lancer chaque moteur**, contrôle tous les modèles installés et teste la connexion à Hugging Face.
- **Copier le rapport** permet de me l'envoyer si quelque chose ne marche pas.

## Problèmes courants
- **Le moteur s'arrête** : le message affiche un code. Code 137 : mémoire insuffisante, choisis une IA plus petite, mets la mémoire de conversation à 2048, ferme les autres applis. Autre code : lance l'analyse complète et envoie-moi le rapport.
- **Moteur absent** : l'APK a été compilé sans les moteurs. Relance le workflow GitHub et lis son journal.
- **Téléchargement refusé ou lien cassé** : cherche le modèle dans Hugging Face ou Web, ou ajoute ton jeton.
- **Fichier invalide** : désinstalle-le et réinstalle-le.
- **Plan illisible dans Projet** : reformule la demande en une phrase claire, ou prends un modèle plus grand.
- **Réponses lentes** : normal sur processeur. Les modèles 0,5B et 1,5B sont les plus rapides.
- **Réponses fausses** : une IA locale est bien moins puissante que les grandes IA en ligne. Vérifie les informations importantes.
