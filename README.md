# FirestEventowki (Paper 1.21.4)

Przedmioty eventowe: Płomienny Miecz, Płonąca Torba, Totem Drugiej Szansy i Płonąca Zbroja (4 elementy).

## Budowanie
`mvn clean package` -> `target/FirestEventowki.jar` do `plugins/`.
Na GitHubie: zakładka Actions -> Artifacts (workflow w `.github/workflows/build.yml`).

## Resource pack (WYMAGANY)
`resourcepack/Firest-Pack.zip` zawiera tekstury eventówek **oraz serca** z pluginu LsSmp.
W `server.properties`: `resource-pack=<bezpośredni link>`, `resource-pack-sha1=<sha1>`, `require-resource-pack=true`.
Bez packa itemy mają fioletową kratkę (można wtedy usunąć `item-model` i `equip-model` z config.yml).

## Komendy (`firest.eventowki.admin`)
- `/firestareventowki give (gracz) (item|zbroja) [ilość]`
- `/firestareventowki panel` - GUI, klik = dostajesz item
- `/firestareventowki lista`, `/firestareventowki reload`
Itemy: miecz, sakwa, totem, helm, napiersnik, spodnie, buty, zbroja (komplet)

## Jak działają
- **Miecz**: Ostrość 7, Zaklęty ogień 2, Niezniszczalność 4.
- **Torba**: PPM otwiera GUI (45 slotów + dolny pasek z przyciskiem WYPŁAĆ). Gdy zabijesz gracza i masz torbę
  w ekwipunku, jego przedmioty trafiają do niej. Klik w przedmiot = bierzesz go. Przedmioty znikają po 15 min.
- **Totem**: miej w ekwipunku; po śmierci tracisz tylko połowę przedmiotów (reszta wraca po respawnie). Zużywa się.
- **Zbroja**: Ochrona 5 + Niezniszczalność 4 + enchanty per element. Pełny komplet = Odporność na ogień + Szybkość I.
Wszystko (nazwy, opisy, enchanty, czasy) edytujesz w `config.yml`.
