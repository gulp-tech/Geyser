# M1: content guard для старых клиентов

Первый практический шаг к диапазону 1.21.x … 26.65. Задача этапа — не дать старому клиенту
**отвалиться из-за контента, которого он не знает** (сущности, звуки, частицы), вместо того
чтобы рвать соединение.

## Что уже в коде

| Класс | Роль |
|---|---|
| `org.geysermc.geyser.compat.ContentKind` | виды контента: `ENTITY`, `SOUND`, `PARTICLE` |
| `org.geysermc.geyser.compat.LegacyContentManifest` | таблица «ключ контента → протокол, в котором он появился» + парсер JSON |
| `org.geysermc.geyser.compat.ContentPolicy` | чистая логика решения (без сессий, пакетов и логов — удобно тестировать) |
| `org.geysermc.geyser.compat.ContentGuard` | проверка исходящих пакетов + ленивая загрузка манифеста |
| `core/src/main/resources/compat/legacy-content.json` | сам манифест (по умолчанию **пустой**) |
| `core/src/main/resources/compat/legacy-content.example.json` | пример с реальными ключами |

Точка подключения — `UpstreamSession.sendPacket` и `sendPacketImmediately`: **все** исходящие
Bedrock-пакеты (включая очередь `postStartGamePackets`) проходят через неё.

```java
public void sendPacket(@NonNull BedrockPacket packet) {
    if (!isClosed() && ContentGuard.shouldSend(packet, guardedProtocolVersion())) {
        session.sendPacket(packet);
    }
}
```

## Формат манифеста

```json
{
  "schema": 1,
  "contents": {
    "entity":   { "minecraft:sulfur_cube": "1.26.50" },
    "sound":    { "eject_block": "26.60" },
    "particle": { "minecraft:sulfur_cube_particle": 2225 }
  }
}
```

Значение — версия Bedrock, в которой контент появился. Можно писать **именем версии**
(`"1.26.50"`, `"26.60"`, `1.` опционально) или **номером протокола** (`2225`).
Имена резолвятся через сгенерированную таблицу `compat/bedrock-versions.json`
(`org.geysermc.geyser.compat.BedrockVersions`): `"26.60"` и `"1.26.60"` — одна и та же версия.
Если протокол клиента меньше указанного, пакет не отправляется.

Правила, заложенные в поведение:

- **Пустой манифест = guard выключен.** Ничего не фильтруется, поведение идентично апстриму.
- **Чего нет в манифесте — считается известным.** Мы никогда не выкидываем контент, о котором
  нет данных: лучше отправить и, возможно, получить проблему, чем молча ломать работающее.
- **Ключи регистронезависимы** (в протокольной библиотеке звуки — `EJECT_BLOCK`, на проводе —
  `eject_block`).
- **Битый JSON/нечисловые значения не роняют сервер** — запись пропускается.
- **Неизвестная версия клиента (этап логина) не фильтруется.**
- Аварийное отключение: `-Dgeyser.compat.disable-content-guard=true`.

## Как включить данные

До этапа M2 (генератор манифестов) таблица пустая. Чтобы проверить механику руками:

```bash
cp core/src/main/resources/compat/legacy-content.example.json \
   core/src/main/resources/compat/legacy-content.json
./gradlew :bootstrap:spigot:build
```

После этого клиент с протоколом ниже `2193` (26.40/26.45) не получит пакет `AddEntityPacket`
для `minecraft:sulfur_cube`, а клиент ниже `2225` — звуки `eject_block` / `absorb_block`.
Вместо дисконнекта в логе появится debug-строка `Compat: dropping entity 'minecraft:sulfur_cube' …`.

## Что покрыто, а что нет

| Контент | Статус |
|---|---|
| Сущности (`AddEntityPacket`) | ✅ M1 |
| Звуки (`LevelSoundEventPacket`) | ✅ M1 |
| Частицы (`SpawnParticleEffectPacket`) | ✅ M1 |
| Блоки (палитра `StartGamePacket`) | ⏳ M2 — фильтрация палитры по версии клиента |
| Предметы / компоненты (`ItemComponentPacket`, `CreativeContentPacket`) | ⏳ M2 |
| Метаданные сущностей (`SetEntityDataPacket`) | ⏳ M2 |
| Ресурспаки (формат манифеста по версиям) | ⏳ M4 |

## Проверка в CI

`core/src/test/java/org/geysermc/geyser/compat/ContentGuardTest.java` (JUnit 5, без сервера):

- пустой манифест ничего не фильтрует;
- парсинг манифеста: известные виды, регистронезависимость, пропуск неизвестных/битых записей;
- политика: `2192 → drop`, `2193 → send`;
- извлечение ключа из реальных пакетов `AddEntityPacket` / `LevelSoundEventPacket` /
  `SpawnParticleEffectPacket`, включая пустые идентификаторы и «чужие» пакеты.

## Ручной тест на клиенте (нужен ваш доступ к старым версиям)

1. Собрать `Geyser-Spigot.jar` (CI: artifact `Geyser-jars`, или локально `./gradlew build`).
2. Положить jar в `plugins/` Purpur-сервера, поднять сервер, зайти современным клиентом —
   убедиться, что всё работает как раньше (манифест пустой → поведение не меняется).
3. Подменить манифест на `legacy-content.example.json`, перезапустить.
4. Зайти клиентом 26.40 и клиентом 26.60 в мир, где есть серные кубы:
   - 26.60 — кубы видны и работают;
   - 26.40 — кубы не появляются, но **соединение не рвётся**, в логе debug-строка guard'а.
5. Если 26.40 всё равно вылетает — значит, ломается не сущность, а что-то ещё
   (метаданные, звук, частица): пришлите лог, расширим покрытие.

## Дальше (M2)

1. Генератор манифестов `tools/compat-gen/` из курируемого датасета версий Bedrock.
2. Фильтрация палитры блоков и предметов до отправки `StartGamePacket`.
3. Конфиг-ключи `compat.*` вместо системного свойства.
4. Первое расширение окна версий (M2 в `docs/version-support-plan.md`).
