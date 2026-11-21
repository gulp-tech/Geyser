# Geyser — разбор исходного кода

> Разбор выполнен по исходникам апстрима **GeyserMC/Geyser**, ветка `master`,
> коммит `cb04ea8af` от 20.11.2026 («Mirror custom block collision boxes on X for 26.60+»),
> версия проекта `2.12.1-SNAPSHOT`, лицензия MIT.
>
> В этом репозитории (`gulp-tech/Geyser`) на ветке `arena/ce260261-geyser` исходников пока нет —
> только `README.md`. Документ описывает именно апстрим, чтобы по нему можно было ориентироваться,
> когда код будет добавлен.

---

## 1. Что делает проект

Geyser — **прокси-мост между Minecraft: Bedrock Edition и Minecraft: Java Edition**.

Bedrock-клиент (телефон, консоль, Windows) подключается по протоколу Bedrock (RakNet/UDP,
в новых версиях ещё и NetherNet/WebRTC), Geyser поднимает у себя вторую сессию — обычный
Java-клиент к настоящему Java-серверу (Paper/Spigot/Fabric/NeoForge/Velocity/BungeeCord) —
и **переводит пакеты и игровую модель** в обе стороны:

```
Bedrock client                  Geyser                          Java server
──────────────                  ──────                          ───────────

UDP/RakNet или WebRTC  ──►  UpstreamSession
                            (BedrockServerSession)
                                   │  BedrockPacket
                                   ▼
                          BEDROCK_PACKET_TRANSLATORS  ────────►  DownstreamSession ──► TCP 25565
                                   ▲                             (MCProtocolLib
                                   │                              ClientSession)
                          JAVA_PACKET_TRANSLATORS  ◄────────────         │
                                   │  BedrockPacket                     │ Packet (Java)
                                   ▼                                    ▼
                            UpstreamSession.sendPacket()  ◄──── отвечает серверу
```

Переводится не только «провод», но и **содержимое**:

- блочные состояния (Java block state ↔ Bedrock block palette),
- предметы, компоненты предметов (1.20.5+ data components → Bedrock item NBT),
- сущности и их метаданные, спавн-правила, хитбоксы,
- чанки (палитра блоков, биомы, высоты, освещение, block entities),
- инвентарь и все клики (Bedrock `ItemStackRequest` ↔ Java `Click Window`),
- текст/чат (Adventure Component ↔ Bedrock raw JSON + переводы),
- звуки, частицы, скорборды, боссбары, тайтлы, world border, диалоги, waypoint'ы,
- resource packs: Java-пак перепаковывается в Bedrock-формат и отдаётся клиенту.

Обратная сторона: часть механик принципиально не переводится (см. «Current Limitations» в вики),
поэтому проект и не обещает 100% паритет.

---

## 2. Поддерживаемые версии (актуально на разбор)

| Edition | Версии |
|---|---|
| Bedrock | 26.40–26.65 (четыре семейства кодеков: `v2168_hotfix4`, `v2169`, `v2193`, `v2225`) |
| Java | ровно одна — 26.3 (`MinecraftCodec.CODEC` из MCProtocolLib) |

Java-версия поддерживается строго одна: старые Java-серверы закрываются через **ViaVersion/ViaProxy**,
а не через множественные кодеки внутри Geyser. Bedrock, наоборот, — «зоопарк» версий, потому что
мобильные клиенты обновляются автоматически.

Ключевой класс: `core/.../network/bedrock/GameProtocol.java` — реестр `BedrockCodec` → список
человекочитаемых версий (`"26.40"`, …), вычисление `DEFAULT_BEDROCK_PROTOCOL`.

---

## 3. Структура репозитория

```
Geyser/
├── ap/                 аннотационный процессор (генерирует списки классов по аннотациям)
├── api/                публичный API: события, расширения, команды, конфиг-контракты (~16.7k LOC)
├── common/             общий код с Floodgate (крипто, news, BedrockData, enum'ы)
├── core/               всё ядро: сеть, сессии, трансляторы, реестры, маппинги (~142k LOC)
├── bootstrap/          платформенные обёртки
│   ├── standalone/     Geyser-Standalone.jar (свой процесс, консоль/GUI)
│   ├── spigot/         плагин Spigot/Paper, инъекция в Netty сервера
│   ├── velocity/       плагин Velocity (прокси)
│   ├── bungeecord/     плагин BungeeCord
│   ├── viaproxy/       плагин ViaProxy
│   └── mod/            Fabric / NeoForge (+ gametest — игровые тесты на стороне сервера)
├── build-logic/        convention-плагины Gradle (публикация, Java, тесты, shadow)
├── gradle/libs.versions.toml   каталог версий зависимостей
└── settings.gradle.kts / build.gradle.kts
```

Объём кода (без сгенерированного и без submodule'ов): **~1400 Java-файлов, ~173k строк**,
из них `core` — 1057 файлов / 142k строк.

Самые крупные файлы — индикатор «горячих» мест:

| Файл | Строк | Что это |
|---|---|---|
| `core/.../level/block/Blocks.java` | 3671 | сгенерированный реестр блоков (mappings-generator) |
| `core/.../session/GeyserSession.java` | 2816 | «бог-объект» сессии: состояние игрока, инвентарь, кэши, отправка пакетов |
| `core/.../item/Items.java` | 1744 | сгенерированный реестр предметов |
| `core/.../entity/VanillaEntities.java` | 1322 | реестр сущностей |
| `core/.../translator/inventory/InventoryTranslator.java` | 1164 | база для всех инвентарей |
| `core/.../GeyserImpl.java` | 1102 | синглтон-ядро: старт, конфиг, реестры, метрики, reload |
| `core/.../entity/type/Entity.java` | 1057 | база сущностей |
| `core/.../network/bedrock/nethernet/NetherNetServer.java` | 858 | WebRTC-транспорт NetherNet |

---

## 4. Сборка и зависимости

- **Java 21** (toolchain в корневом `build.gradle.kts`); CI собирает на JDK 25.
- **Gradle (Kotlin DSL)** + `build-logic` convention-плагины, `gradle/libs.versions.toml` как каталог версий.
- Обязательный шаг — **submodule'ы**: `git submodule update --init --recursive`
  (`core/src/main/resources/mappings` ← GeyserMC/`mappings`, `core/src/main/resources/languages` ← GeyserMC/`languages`).
- Плагины сборки: `indra` (git-версия), `blossom` (подстановка версии/коммита в исходники),
  `shadow` (fat-jar), `lombok`, `architectury-loom` (для Fabric/NeoForge), `minotaur` (Modrinth).
- Прочее: во время сборки скачивается `GeyserIntegratedPack.mcpack`
  (`core:downloadIntegratedPack`), а `:core:downloadBedrockData` обновляет блоки/предметы/сущности
  из `CloudburstMC/Data`.
- Порядок запуска: `./gradlew build` → артефакты в `bootstrap/*/build/libs`.

Основные зависимости:

| Область | Библиотека |
|---|---|
| Bedrock-протокол | `CloudburstMC/Protocol` (Beta15), `CloudburstMC/Network` (RakNet + NetherNet) |
| Java-протокол | `GeyserMC/MCProtocolLib` (`26.3-4`) |
| Транспорт | Netty 4.2 (`epoll`, `kqueue`, `io_uring`), `libdatachannel` (WebRTC), BouncyCastle |
| Аутентификация | `MinecraftAuth` (Microsoft/OAuth device code) |
| Текст | Adventure 5.x |
| Команды | Cloud (`cloud-core`, `cloud-minecraft`) |
| Конфиг | Configurate (интерфейсы + YAML) |
| Утилиты | fastutil, Guava, Gson, bStats, snakeyaml |
| Оптимизация чанков | `GeyserMC/erosion` (внешний агент, Unix-сокет) |

---

## 5. Как устроена трансляция (главная идея архитектуры)

### 5.1 Аннотация + кодоген

Трансляторы — это объекты вида:

```java
@Translator(packet = ClientboundBlockUpdatePacket.class)
public class JavaBlockUpdateTranslator extends PacketTranslator<ClientboundBlockUpdatePacket> {
    @Override
    public void translate(GeyserSession session, ClientboundBlockUpdatePacket packet) { … }
}
```

`ap/` (аннотационный процессор) на этапе компиляции собирает все классы с `@Translator`
и пишет их имена в сгенерированный метафайл. При старте `PacketRegistryPopulator.populate()`
читает этот список (`AnnotationUtils.getGeneratedClassesForAnnotation`), создаёт по одному
экземпляру через reflection и раскладывает в `Registries.JAVA_PACKET_TRANSLATORS`
и `Registries.BEDROCK_PACKET_TRANSLATORS` (ключ — класс пакета).

Плюсы: нулевая цена dispatch'а, отсутствие ручной регистрации, расширения могут добавлять свои
трансляторы. Минус: магия на этапе сборки, ошибки видны только в рантайме (лог «Could not instantiate…»).

Тот же процессор обслуживает `BlockEntityProcessor`, `CollisionRemapperProcessor`, `SoundHandlerProcessor`.

### 5.2 Направления

| Направление | Где | Кол-во |
|---|---|---|
| Java → Bedrock | `core/.../translator/protocol/java/**` | **122** класса с `@Translator` |
| Bedrock → Java | `core/.../translator/protocol/bedrock/**` и подпакеты `entity/`, `world/` | **36** |

Java→Bedrock — основная масса (сервер шлёт мир, Geyser его рисует клиенту).
Bedrock→Java — ввод игрока: движение, клики, чат, команды, respawn и т.п.

### 5.3 Потоки

- На **каждую** Bedrock-сессию — свой Netty event loop; основная игровая логика сессии
  выполняется в нём (кроме пакетов, для которых `shouldExecuteInEventLoop() == false`).
- Исходящие в Bedrock/Java пакеты отправляются через `GeyserSession.sendUpstreamPacket` /
  `sendDownstreamPacket`; последний сам переключается в event loop канала, если вызван извне.
- Тяжёлые вещи (генерация resource packs, загрузка скинов, работа с маппингами) — в отдельных
  пулах/`CompletableFuture`.

### 5.4 Типичный жизненный цикл

```
Bedrock login (LoginPacketHandler)  ─►  проверка Xbox/offline/Floodgate
        │
        ▼  GeyserSession создаётся, подключается к Java-серверу (GeyserSessionAdapter)
Java handshake/login (auth-type: online | offline | floodgate)
        │
        ▼  JavaLoginTranslator  (ClientboundLoginPacket) — «StartGame»
        ├─ выбирает BedrockDimension (overworld/nether/the_end + кастомные)
        ├─ отправляет StartGamePacket, CreativeContent, BiomeDefinitionList,
        │  AvailableEntityIdentifiers, PlayerList, ChunkRadius…
        └─ дальше пакеты идут потоком через реестры трансляторов
        ▼
BedrockSetLocalPlayerAsInitializedTranslator — игрок «в мире», начинают идти чанки
```

Ключевые точки входа:
`GeyserImpl.initialize()` → `startInstance()` → `GeyserServerInitializer` (RakNet/NetherNet) →
`UpstreamPacketHandler` (Bedrock) и `GeyserSessionAdapter` (Java).

---

## 6. Модель данных мира: маппинги

Две большие «таблицы перевода», без которых не работает ничего:

1. **Блоки.** Java-состояние блока (`BlockState`, int id) → Bedrock `block_palette` id
   (`BlockMappings.getBedrockBlockId`), плюс свойства (facing, half, waterlogged…) и
   collision-shape'ы.
2. **Предметы.** Java `Item` + data components → Bedrock item NBT (`ItemMappings`), runtime id,
   креативный контент, прочность, зачарования, баннеры, черепа, кастомные предметы.

Источники данных:

- `core/src/main/resources/bedrock/` — данные Bedrock, скачанные из `CloudburstMC/Data`:
  `block_palette.<ver>.nbt`, `creative_items.<ver>.json`, `runtime_item_states.<ver>.json`,
  `item_components.<ver>.nbt`, `entity_identifiers.dat`, `biome_definitions.dat`, геометрии, скины.
  Версии датасетов дублируются по мере роста Bedrock (сейчас `26_40`, `26_50`, `26_60`).
- `core/src/main/resources/mappings/` — submodule GeyserMC/mappings: `blocks.json`,
  `items.json`, `biomes.json`, `entities.json`, версионные оверрайды
  (`versions/block`, `versions/item`, `versions/skull`, `versions/waypoint`).
- `core/src/main/resources/languages/` — submodule переводов (сообщения Geyser, Crowdin).

Реестры строятся лениво: `Registries.load()` + `SimpleDeferredRegistry`/`*DeferredRegistry`,
загрузчики — `core/.../registry/loader/*RegistryLoader`. Кастомные блоки/предметы/сущности
добавляются расширениями через события `GeyserDefineCustomBlocksEvent`,
`GeyserDefineCustomItemsEvent`, `GeyserDefineEntitiesEvent`.

`Blocks.java`, `Items.java`, `VanillaEntities.java` **генерируются** внешним
`mappings-generator` — руками их не правят.

---

## 7. Сеть

### 7.1 Входящая сторона (Bedrock)

- `RaknetServer` — классический UDP/RakNet (`network/raknet`), поддержка HAProxy PROXY protocol,
  whitelist IP.
- `NetherNetServer` — новый транспорт Bedrock на WebRTC (`libdatachannel` + сигналинг):
  режимы `builtin` (свой сигналинг по TCP на Bedrock-порту), `nxs` (внешний сервис),
  `hybrid`. Настройки — секция `bedrock.signaling`, команда `/geyser nethernet`.
- `CodecProcessor` выбирает кодек по версии клиента (диапазон поддерживаемых протоколов),
  `GameProtocol` хранит список.
- Пинг: `BedrockPingHandler` + `IGeyserPingPassthrough` (проброс пинга на Java-сервер для MOTD/онлайна).

### 7.2 Исходящая сторона (Java)

`network/java` — тонкий слой, чтобы «притвориться» игроком внутри процесса сервера:

- `GeyserInjector` — на Spigot/Paper встраивается в Netty-пайплайн сервера и подключается
  к локальному каналу вместо реального TCP (`LocalChannelWrapper`, `LocalSession`);
  на остальных платформах — обычный TCP-коннект.
- `ChannelWrapper` / `IoHandlerWrapper` / `DefaultChannelPipelinePublic` — обёртки, чтобы
  обойти ограничения доступа к внутренним классам.
- Онлайн-режим: шифрование и Mojang-аутентификация через MCProtocolLib; для Floodgate —
  данные Bedrock-игрока шифруются AES и упаковываются в hostname (`BedrockData`),
  скины загружаются через Floodgate skin uploader.

---

## 8. Аутентификация

| `java.auth-type` | Поведение |
|---|---|
| `online` | Полноценный Microsoft/Xbox-логин на каждый Bedrock-аккаунт. Токены можно кэшировать (`savedUserLogins` → `saved-refresh-tokens.json`). Есть сценарий «подтверди вход в браузере» с таймаутом (`pendingAuthenticationTimeout`, `PendingMicrosoftAuthentication`). |
| `offline` | Любой ник, без проверки. |
| `floodgate` | Аутентификацию делает Floodgate на стороне Java-сервера, Geyser лишь шифрует Bedrock-данные. |

Дополнительно: проверка подписи Bedrock-логина (`validate-bedrock-login`), Code of Conduct
(события `SessionAcceptCodeOfConductEvent` / `ServerCodeOfConductEvent`), лимиты и
`ConnectionRequestEvent` для кастомных фильтров подключения.

---

## 9. Инвентарь и ввод — самая «болезненная» часть

Bedrock и Java по-разному представляют клики:

- Bedrock шлёт `ItemStackRequest` (транзакции со списком действий), Java — `Click Window`
  с режимами (shift, drag, number-key, throw…) и подтверждениями (`ConfirmTransaction`).
- Поэтому в `translator/inventory/**` живут отдельные реализации для каждого контейнера:
  сундук, печь, наковальня, лошадь, крафт, камнерез, кузнечный стол, зельевар, бедрок…
  (`InventoryTranslator` + наследники, `inventory/click/ClickPlan`).
- Рецепты: `JavaUpdateRecipesTranslator`, `recipe/GeyserRecipe`, `TrimRecipes`,
  `GeyserStonecutterData` — конвертация Java-рецептов в Bedrock-рецепты (в т.ч. кастомные).
- Книга/блокнот — отдельные кэши (`BookEditCache`, `LecternContainer`).
- `BedrockItemStackRequestTranslator` и `BedrockInventoryTransactionTranslator` — входные точки.

---

## 10. Расширения и публичный API

Модуль `api` — то, что видят разработчики плагинов и расширений:

- **`Extension` / `ExtensionManager`** — загрузка `.jar` из папки `extensions`, свой
  classloader, `extension.yml`-описание, свой логгер.
- **События** (`api/.../event`): lifecycle (`GeyserPreInitializeEvent`, `GeyserPostInitializeEvent`,
  `GeyserPreReloadEvent`, `GeyserShutdownEvent`, `GeyserDefineCustomBlocksEvent`,
  `GeyserDefineResourcePacksEvent`, `GeyserRegisterPermissionsEvent`…),
  bedrock (`SessionLoginEvent`, `SessionJoinEvent`, `SessionDisconnectEvent`, `SessionSkinApplyEvent`,
  `SessionSpawnEntityEvent`…), connection (`ConnectionRequestEvent`, `GeyserBedrockPingEvent`),
  java (`ServerSpawnEntityEvent`, `ServerDefineCommandsEvent`, `ServerTransferEvent`…).
- **`GeyserApi`**: `connectionByUuid`, `connectionByXuid`, `onlineConnections`,
  `extensionManager`, `eventBus`, `defaultRemoteServer`, `bedrockListener`,
  `configDirectory`, `packDirectory`, `platformType`, `supportedJavaVersion`,
  `supportedBedrockVersions`, `consoleCommandSource`.
- **Команды** через Cloud: `/geyser <help|reload|stop|list|dump|version|statistics|settings|offhand|ping|advancements|extensions|nethernet|gamerule|connectiontest|customoptions|quickactions|advancedtooltips>`.
  Права — `geyser.command.*`, дефолты в `permissions.yml`.
- **Cumulus-формы**: `FormCache`/`session/dialog` — серверные UI-диалоги и меню Bedrock.

Для собственных расширений API публикуется в maven (`org.geysermc.geyser:api`), версия API
привязана к `base-api`.

---

## 11. Конфигурация

- `config.yml` (YAML) описывается **интерфейсом** `GeyserConfig` (Configurate interfaces) —
  один источник правды для полей, дефолтов, комментариев и миграций.
- Разделы: `bedrock` (порт, clone-remote-port, compression, proxy-protocol, `signaling` для NetherNet),
  `java` (адрес, auth-type, forward-hostname), `motd`, `gameplay` (сложности с хитбоксами,
  координатами, элитрами, «show-coordinates», «force-resource-packs» …),
  `advanced` (bedrock/java/nxs подразделы, debug, cache-tokens…), `default-locale`, `metrics`.
- Платформенные ограничения — аннотации `@IncludePlatform` / `@ExcludePlatform`,
  переопределения через env (`EnvironmentOverrides`), миграции (`ConfigMigrations`,
  `configVersion`), перестановка комментариев (`ConfigurationCommentMover`).
- Отдельный `GeyserPluginConfig` / `GeyserRemoteConfig` для встроенного (plugin) режима,
  где часть настроек берётся у Java-сервера.

---

## 12. Тесты и CI

- Юнит-тесты: `core/src/test` (~20 классов) — конфиг, миграции, дампы секретов, `CodecProcessor`,
  `RaknetServer`, `MessageTranslator`, парсеры маппингов, скорборд, nethernet-сигналинг.
- `bootstrap/mod/gametest` — **игровые тесты** внутри Fabric/NeoForge: генерация ожидаемых
  метаданных сущностей (`data/geyser/test_instance/entity_metadata/*.json`) и проверка,
  что для каждого Java-пакета есть транслятор (`JavaPacketTranslatorExistenceTest`).
- CI (`.github/workflows`): `build.yml` собирает все 7 артефактов и публикует в maven на
  `master`/`api/*`/`feature/*`; `pull-request.yml`, `preview.yml` (превью-сборки),
  `build-remote.yml`, `dispatch-preview.yml`.
- Политика: `AGENTS.md` ссылается на AI-политику организации; `CONTRIBUTING.md` — стиль кода
  (fastutil-мапы, `@Nullable` из Checker Framework, `switch`-стрелки, Javadoc).

Покрытие тестами при этом точечное: **трансляторы почти не покрыты** — их проверяют вручную
и через gametest-снапшоты.

---

## 13. Наблюдения и «за что цепляться»

1. **GeyserSession — 2800 строк.** Центральный объект, куда стянуто почти всё состояние сессии.
   Почти любая фича трогает его; рефакторинг — самая дорогая задача в проекте.
2. **Генерация кода.** `Blocks`/`Items`/`VanillaEntities` и списки классов для реестров
   генерируются. Правки «руками» бессмысленны — нужно менять генератор/маппинги
   (внешний репозиторий `mappings`).
3. **Submodule'ы обязательны.** Без `mappings` и `languages` проект не соберётся и не запустится.
4. **Две разные скорости развития.** Java-версия одна (её поднимает MCProtocolLib), Bedrock —
   четыре семейства кодеков с датасетами; при обновлении Bedrock нужно обновлять
   `bedrock/*.nbt` + `GameProtocol`.
5. **NetherNet — свежая и рискованная зона.** WebRTC-транспорт, нативные библиотеки
   (`libdatachannel`, BouncyCastle, сигналинг) — самая «системная» часть кода.
6. **Инвентарь — самая сложная логика.** Ошибки кликов и «предметы-призраки» — классические баги.
7. **Производительность.** Основные рычаги: `erosion` (перенос конвертации чанков в агент
   на стороне сервера), кэш чанков, `hasOwnChunkCache()` у платформ с прямым доступом,
   настройки `chunk-radius`/`cache-chunks`.
8. **Платформенные «костыли».** Spigot-инъектор (рефлексия в `MinecraftServer`, поиск
   `ServerSocketChannel`) и обёртки Netty — самая хрупкая часть после NetherNet: ломается
   на форках Paper. Совместимость с Paper-API проверяется отдельным тестом
   (`PaperApiCompatibilityTest`), а Folia API подключён только как `compileOnly`.

---

## 14. Быстрый навигатор «где что менять»

| Задача | Куда смотреть |
|---|---|
| Добавить/починить перевод Java-пакета | `core/.../translator/protocol/java/**`, класс с `@Translator(packet = …)` |
| Обработать ввод Bedrock | `core/.../translator/protocol/bedrock/**` |
| Блок не той формы/текстуры | `registry/mappings/**`, `level/block/**`, `mappings/blocks.json` |
| Предмет отображается неверно | `item/**`, `registry/populator/ItemRegistryPopulator`, `mappings/items.json` |
| Сущность не спавнится / не те метаданные | `entity/factory`, `entity/type/**`, `EntityMetadata`-трансляторы |
| Чанк не грузится / дырки в мире | `translator/protocol/java/level/JavaLevelChunkWithLightTranslator.java`, `level/chunk/**` |
| Инвентарь/клики | `translator/inventory/**`, `inventory/click/**` |
| Новый конфиг-параметр | `configuration/GeyserConfig.java` (+ `ConfigMigrations` при переименовании) |
| Новая команда | `command/defaults/**` + права в `Permissions.java` / `permissions.yml` |
| Событие для расширений | `api/.../event/**` + `GeyserImpl`/`GeyserSession` (fire) |
| Новый транспорт/сеть | `network/**`, `network/bedrock/nethernet/**`, `GameProtocol.java` |

---

## 15. Что не поддерживается (важно для ожиданий)

Проект честно перечисляет ограничения в вики (`Current Limitations`), ключевые из них:
не всё работает с античитами (движение), часть entity flags, отдельные механики Java
(например, некоторые аспекты combat/щитов, статистика, часть данных о мире) не имеют
аналога в Bedrock. Поэтому «полного паритета» не будет никогда — это ограничение самих игр.

---

## 16. Что можно сделать дальше в этом репозитории

1. **Добавить исходники в репозиторий** (сейчас их тут нет): форк апстрима на нужном коммите,
   с submodule'ами `mappings` и `languages`.
2. **Собрать проверенно**: `./gradlew build` (нужен JDK 21+, доступ к maven-репозиториям
   `repo.opencollab.dev`, `maven.fabricmc.net`, `maven.neoforged.net`).
3. **Зафиксировать точку старта**: тег/ветку под свою версию, CI, `.gitignore` под артефакты.
4. **Определить цель форка** (кастомные предметы/блоки, свои переводы, интеграция с плагинами,
   оптимизации чанков) — и уже от неё строить список задач по таблице из §14.
