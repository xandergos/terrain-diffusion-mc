This text is in french cause' I speak french and I don't take the time to translate this file. Sorry  :D

# River Debug Overlays


Documentation rapide des overlays de debug utilisés par le pipeline de rivières globales.

Ces overlays sont accessibles en jeu via le menu **F8**.

## Objectif

Les overlays servent à vérifier visuellement chaque étape du pipeline hydrologique avant de générer les rivières finales.

Pipeline actuel :

```text
Low-res heightmap
→ Cost map
→ Flow direction
→ Flow accumulation
→ River cell extraction
```

Chaque étape doit rester lisible séparément. Si un résultat semble faux il faut debugger l'étape précédente avant de corriger la suivante.

---

## Low-res heightmap

Affiche l'altitude brute de la heightmap basse résolution.

| Couleur | Signification                |
|---|------------------------------|
| Bleu foncé | Très bas                     |
| Bleu clair | Bas                          |
| Vert | Altitude moyenne -> plaines  |
| Jaune-brun | Haut                         |
| Blanc | Très haut -> sommets         |
| Bordures noires | Limites des cellules low-res |

### À vérifier

- Les grandes formes du terrain doivent être cohérentes.
- Les montagnes doivent ressortir clairement.
- Les plaines et vallées doivent être visibles à l'échelle low-res.
- L'overlay doit être continu quand le joueur traverse des frontières de chunks.

Si la heightmap est incohérente les étapes suivantes seront fausses.

---

## Cost map

Affiche les coûts utilisés pour influencer le passage des futures rivières.

Le coût indique où une rivière **préfère** passer. Il ne doit pas remplacer la gravité.

### Final cost, slope cost, ridge cost, biome cost, rock/soil cost and forbidden cost

| Couleur | Signification                   |
|---|---------------------------------|
| Vert | Coût faible -> zone favorable   |
| Jaune | Coût moyen                      |
| Rouge | Coût élevé -> zone défavorable  |
| Blanc | Coût très élevé -> quasi bloquant |

### Valley bonus

Le `valley bonus` n'est pas un coût. C'est un bonus appliqué aux fonds de vallée.

| Couleur | Signification                     |
|---|-----------------------------------|
| Noir / sombre | Pas une vallée                    |
| Bleu | Vallée faible                     |
| Cyan | Vallée forte -> zone très favorable |

### À vérifier

- Les fonds de vallée doivent tendre vers le vert dans `Final cost`.
- Les crêtes et montagnes doivent tendre vers rouge/blanc.
- Le `Valley bonus` doit ressortir en bleu/cyan dans les vallées.
- Le `Slope cost` doit être fort sur les pentes raides.
- Le `Ridge cost` doit pénaliser les lignes de crête.
- Le `Biome cost` doit différencier les biomes quand le sampler biome sera branché.

### Rappel important

Une zone à faible coût n'autorise pas une rivière à monter une colline.

La gravité est gérée dans l'étape `Flow direction`.

---

## Flow direction

Affiche la direction d'écoulement choisie pour chaque cellule low-res.

La direction est calculée à partir de la heightmap et de la cost map.

### Arrows

| Couleur | Signification                                 |
|---|-----------------------------------------------|
| Cyan | Écoulement valide vers une cellule plus basse |
| Orange | Écoulement plat -> sans descente nette        |
| Rouge | Sink -> dépression locale                       |
| Rien | Bord de fenêtre debug ou cellule ignorée      |

### Decision score

Affiche le score de décision du voisin choisi.

| Couleur | Signification               |
|---|-----------------------------|
| Vert | Bon choix -> coût faible      |
| Jaune | Choix moyen                 |
| Rouge | Choix coûteux               |
| Blanc | Choix très mauvais ou forcé |

### Selected drop

Affiche la chute verticale choisie entre la cellule courante et la cellule cible.

| Couleur | Signification |
|---|---|
| Bleu foncé | Très faible descente |
| Bleu clair / cyan | Descente nette |
| Blanc | Forte descente |

### Sinks / depressions

Affiche uniquement les cellules sans direction valide.

| Couleur | Signification                                     |
|---|---------------------------------------------------|
| Rouge | Dépression locale -> aucun voisin plus bas          |
| Rien | Cellule avec écoulement valide ou bord de fenêtre |

### À vérifier

- Les flèches doivent globalement descendre.
- Les flèches ne doivent pas traverser les crêtes sans raison.
- Les zones plates peuvent apparaître en orange.
- Les sinks rouges peuvent représenter des lacs futurs ou des artefacts à corriger.
- Les bords de la fenêtre debug ne doivent pas être interprétés comme des sinks.

---

## Flow accumulation

Affiche combien de cellules amont drainent vers chaque cellule low-res.

Chaque cellule contribue une unité de ruissellement. Une cellule avec une forte accumulation est donc un candidat naturel pour devenir une rivière à l'étape d'extraction.

### Linear heatmap

Affiche l'accumulation avec une échelle linéaire.

| Couleur | Signification                         |
|---|---------------------------------------|
| Bleu très sombre | Accumulation minimale -> cellule isolée |
| Bleu / cyan | Faible à moyenne accumulation         |
| Jaune | Accumulation importante               |
| Rouge | Très forte accumulation               |
| Blanc | Maximum local de la fenêtre debug     |

À utiliser pour repérer les collecteurs principaux. Sur une grande fenêtre, cette couche peut écraser les petits affluents visuellement.

### Log heatmap

Affiche l'accumulation avec une échelle logarithmique.

| Couleur | Signification |
|---|---|
| Bleu très sombre | Très faible accumulation |
| Bleu / cyan | Petits collecteurs visibles |
| Jaune | Affluents moyens |
| Rouge / blanc | Axes de drainage dominants |

C'est la meilleure couche pour debugger. Elle montre mieux les petits affluents qu'une échelle linéaire.

### Sinks / depressions

Affiche uniquement les dépressions internes.

| Couleur | Signification |
|---|---|
| Rouge | Sink, dépression locale sans sortie |
| Rien | Cellule avec sortie valide ou bord de fenêtre |

C'est la couche à utiliser pour repérer les futurs lacs et les cuvettes à traiter.

### Window outlets

Affiche uniquement les cellules où l'eau sort de la fenêtre debug.

| Couleur | Signification |
|---|---|
| Bleu | Outlet sur le bord de la fenêtre debug |
| Rien | Cellule qui ne sort pas de la fenêtre debug |

Les outlets bleus en bord de fenêtre ne sont pas des erreurs. Ils indiquent seulement que le domaine debug est tronqué. Une vraie carte globale n'aurait pas ces limites artificielles, sauf sur les bords du domaine précalculé.

### Sinks + outlets

Affiche les terminaux principaux dans une seule couche.

| Couleur | Signification                                                             |
|---|---------------------------------------------------------------------------|
| Bleu | Outlet sur le bord de la fenêtre debug. Rendu volontairement plus discret |
| Rouge | Sink -> dépression locale sans sortie                                       |
| Magenta | Cycle détecté dans les directions d'écoulement                            |
| Rien | Cellule non terminale                                                     |

S'il n'y a que du bleu sur le contour la fenêtre affichée n'a probablement pas de sink interne détecté. Utilise `Sinks/depressions` pour vérifier sans le bruit visuel des bords.

### Cycles

Affiche uniquement les boucles dans le graphe de flow direction.

| Couleur | Signification |
|---|---|
| Magenta | Cycle d'écoulement, généralement causé par un plateau ou une égalité de hauteur |
| Rien | Pas de cycle détecté |

Un cycle n'est pas acceptable pour l'extraction finale des rivières. Il devra être résolu par le traitement des dépressions, des flats et des lacs.

### À vérifier

- Les axes d'accumulation doivent suivre les vallées visibles dans la heightmap.
- La couche `Log heatmap` doit révéler des branches amont et pas seulement un gros trait blanc.
- Les sinks rouges doivent correspondre à des cuvettes crédibles ou à des artefacts de low-res.
- Les cycles magenta doivent rester rares. S'ils sont nombreux, la gestion des plats doit être améliorée.
- `Sinks/depressions` doit être utilisé pour chercher les cuvettes internes sans le bruit des bords.
- `Window outlets` doit surtout afficher du bleu sur les bords de la fenêtre debug.

---

## River cell extraction

Affiche les cellules low-res extraites comme rivières à partir de l'accumulation.

Une cellule devient rivière si :

```text
flowAccumulation >= River threshold
```

Le seuil est réglable dans le menu F8 via **River threshold**. Plus le seuil est bas plus on verra de petits affluents. Plus il est haut plus seules les rivières principales restent visibles.

Cette étape reste raster. Elle sert à valider le réseau hydrologique avant la vectorisation en polylignes.

### River cells

Affiche toutes les cellules considérées comme rivière.

| Couleur | Signification                          |
|---|----------------------------------------|
| Bleu | Cellule rivière proche du seuil        |
| Cyan | Cellule rivière moyenne                |
| Blanc / bleu clair | Forte accumulation -> rivière principale |

### Classified

Affiche les cellules rivière avec leur rôle local.

| Couleur | Signification                                           |
|---|---------------------------------------------------------|
| Vert | Source raster -> aucun affluent rivière en amont        |
| Cyan / bleu | Segment rivière normal                                  |
| Jaune | Confluence -> au moins deux cellules rivière arrivent ici |
| Rouge | Terminal dans une dépression / sink                     |
| Bleu soutenu | Terminal sur bord de fenêtre debug                      |
| Violet | Terminal interne où le tracé quitte le masque rivière   |

### Sources

Affiche uniquement les sources raster.

| Couleur | Signification |
|---|---|
| Vert | Début d'un tronçon rivière selon le seuil courant |
| Rien | Pas une source |

Attention : une source raster n'est pas forcément une vraie source hydrologique. Si on baisse le seuil la source remonte vers l'amont. Si on montes le seuil elle descend vers l'aval. Normal.

### Confluences

Affiche uniquement les confluences raster.

| Couleur | Signification |
|---|---|
| Jaune | Au moins deux cellules rivière amont convergent ici |
| Rien | Pas une confluence |

### Terminals

Affiche uniquement les fins de tracés rivière.

| Couleur | Signification |
|---|---|
| Rouge | La rivière termine dans un sink / dépression |
| Bleu soutenu | La rivière sort par le bord de la fenêtre debug |
| Violet | La rivière quitte le masque rivière à cause du seuil |
| Rien | Pas un terminal |

Un terminal bleu sur le bord du radius renderer est normal. C'est une limite de visualisation, pas une embouchure globale.

### À vérifier

- `River cells` doit suivre les lignes fortes de `Accumulation : Log heatmap`.
- `Classified` doit montrer des sources en amont et des confluences aux jonctions visibles.
- Le seuil ne doit être ni trop bas sinon tout devient rivière ni trop haut sinon le réseau devient pauvre et discontinu.
- Les terminaux rouges indiquent des dépressions à traiter avec la future étape lacs/flats.
- Les terminaux violets indiquent souvent un seuil trop haut ou une accumulation interrompue localement.

---

## Ordre recommandé de debug

1. Activer **Low-res heightmap**.
2. Vérifier les formes générales du relief.
3. Activer **Cost map : Final cost**.
4. Vérifier que les vallées sont favorables et les crêtes défavorables.
5. Activer **Cost map : Valley bonus**.
6. Vérifier que les vallées ressortent clairement.
7. Activer **Flow direction : Arrows**.
8. Vérifier que les flèches descendent.
9. Activer **Flow direction : Sinks/depressions**.
10. Identifier les zones qui deviendront des lacs ou nécessiteront un traitement de dépression.
11. Activer **Accumulation : Log heatmap**.
12. Vérifier que les collecteurs suivent les vallées.
13. Activer **Accumulation : Sinks/depressions**.
14. Vérifier les cuvettes internes.
15. Activer **Accumulation : Window outlets** si on veut vérifier où l'eau quitte la fenêtre debug.
16. Activer **Rivers : River cells**.
17. Ajuster **River threshold** jusqu'à obtenir un réseau lisible.
18. Activer **Rivers : Classified**.
19. Vérifier sources, confluences et terminaux.

---

## Pièges à éviter

- Ne pas interpréter la cost map comme une carte d'écoulement.
- Ne pas autoriser les rivières à monter simplement parce qu'une cellule a un coût faible.
- Ne pas générer les directions indépendamment par chunk.
- Ne pas traiter les bords de la fenêtre debug comme des dépressions réelles.
- Ne pas confondre source raster et vraie source hydrologique. La source raster dépend directement du seuil d'accumulation.
- Ne pas vectoriser tant que les terminaux rouges/violets ne sont pas compris. Sinon on vectorise du bruit.
- Ne pas confondre `Valley bonus` avec un coût négatif absolu : c'est une influence et non pas une règle physique.
- Ne pas masquer les sinks trop tôt. Ils sont utiles pour identifier les futurs lacs et les problèmes de relief.
- Ne pas utiliser l'accumulation linéaire seule pour juger les affluents. Utiliser aussi `Log heatmap`.
- Ne pas ignorer les cycles magenta. Ils cassent l'accumulation finale et l'extraction des rivières.

---

## Convention générale des couleurs

| Couleur | Lecture générale |
|---|---|
| Bleu | Bas, vallée, eau potentielle ou descente |
| Vert | Favorable |
| Jaune | Moyen |
| Rouge | Défavorable, problème, dépression ou très forte accumulation |
| Magenta | Cycle ou boucle d'écoulement |
| Blanc | Valeur extrême |
| Noir | Absence de signal ou bordure |

Cette convention n'est pas universelle : le `Valley bonus` utilise bleu/cyan pour représenter une zone favorable tandis que les coûts utilisent vert pour les zones favorables.
