# О этом форке

Репозиторий — форк [GeyserMC/Geyser](https://github.com/GeyserMC/Geyser) (MIT, см. `LICENSE`).

| | |
|---|---|
| Апстрим | `https://github.com/GeyserMC/Geyser` |
| Ветка апстрима | `master` |
| Коммит | `cb04ea8afe63e76b7c8219810faa47534bf7f2fc` |
| Дата коммита | 20.11.2026 |
| Версия проекта | `2.12.1-SNAPSHOT` |
| Поддерживаемые версии на момент форка | Bedrock `26.40`–`26.65`, Java `26.3` |

## Отличия от апстрима на текущий момент

1. **Подмодули вендорены как обычные каталоги** (в апстриме это git-submodule):
   - `core/src/main/resources/mappings` ← `GeyserMC/mappings`
   - `core/src/main/resources/languages` ← `GeyserMC/languages`

   Сделано, чтобы сборка не требовала `git submodule update --init --recursive`.
   Файл `.gitmodules` удалён, чтобы не вводить в заблуждение.

2. **CI-сборка** `.github/workflows/build-fork.yml`: собирает артефакты и кладёт в
   artifact `Geyser-jars` (в т.ч. `Geyser-Spigot.jar`). Апстримовый workflow не используется —
   он завязан на приватные action'ы и секреты GeyserMC.

3. **Документация проекта** в `docs/`:
   - `geyser-overview.md` — разбор архитектуры апстрима;
   - `bedrock-version-support.md` — почему старые клиенты получают `Outdated client!`;
   - `version-support-plan.md` — план расширения окна версий до 1.21.x … 26.65.

## Как обновляться с апстримом

```bash
git remote add upstream https://github.com/GeyserMC/Geyser.git
git fetch upstream master
git merge upstream/master      # или git rebase upstream/master
```

При обновлении апстрима не забудьте синхронизировать вендоренные каталоги:

```bash
rm -rf core/src/main/resources/mappings core/src/main/resources/languages
git clone --depth 1 https://github.com/GeyserMC/mappings core/src/main/resources/mappings
git clone --depth 1 https://github.com/GeyserMC/languages  core/src/main/resources/languages
rm -rf core/src/main/resources/{mappings,languages}/.git
```

## Сборка

Нужен JDK 21+ и доступ к maven-репозиториям (`repo.opencollab.dev`, `maven.fabricmc.net`,
`maven.neoforged.net`, Maven Central).

```bash
./gradlew build          # все платформы
./gradlew :bootstrap:spigot:build   # только Geyser-Spigot
```

Готовые jar: `bootstrap/*/build/libs/`, `bootstrap/mod/*/build/libs/`.
