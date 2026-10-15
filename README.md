# Deepwaater

Мод для **Minecraft 1.21.1 (NeoForge)** с новым биомом **«Глубокое болото» (`deepwaater:deep_bog`)**.

## Что добавляет мод

- **Сеноты** (`deepwaater:cenote`) — глубокие круглые ямы (16–40 блоков) до грунтовой воды.
  Стенки обрастают мхом, на них висят лианы, на дне глина, а вода поднимается примерно до середины ямы.
- **Большие болотные деревья** (`deepwaater:swamp_tree`) — высокие деревья (22–34 блока) с толстым стволом 2×2,
  широкой кроной, длинными ветвями и корнями у основания. Сделаны из мангрового дерева.
- **Биом** — тёплое влажное болото рядом с побережьем. Вода тёмно-зелёная, трава с болотным оттенком.
  Мобы: зомби, пауки, слаймы, ведьмы, лягушки, летучие мыши, кальмары.

Биом подключается к миру через библиотеку **TerraBlender** (см. `DeepBogRegion.java`).

## Структура проекта

```
src/main/java/com/evizka/deepwaater/
  DeepWaater.java                 — точка входа мода, регистрация региона TerraBlender
  worldgen/
    ModBiomes.java                — ключ биома
    ModFeatures.java              — регистрация фич (сеноты, деревья)
    DeepBogRegion.java            — где на карте климата появляется биом
    CenoteFeature.java            — генерация сенотов
    SwampTreeFeature.java         — генерация болотных деревьев
    WorldGenUtil.java             — общие помощники (запись блоков в чанк, хэш-шум)
src/main/resources/
  data/deepwaater/worldgen/
    biome/deep_bog.json           — сам биом: цвета, мобы, пещеры, список фич
    configured_feature/           — параметры фич (размер ям, высота деревьев, блоки)
    placed_feature/               — правила размещения (частота, высота, где можно)
  assets/deepwaater/lang/         — названия (en_us, ru_ru)
  templates/META-INF/neoforge.mods.toml
```

## Сборка и запуск

Нужен **JDK 21**. Gradle подтянется из wrapper.

```bash
./gradlew runClient     # запустить Minecraft с модом (Windows: gradlew.bat runClient)
./gradlew build         # собрать jar в build/libs/
```

В IDE (IntelliJ IDEA) откройте `build.gradle` как проект и запустите конфигурацию **Minecraft Client**.

Для игры с готовым jar нужны **NeoForge 21.1.x** и мод **TerraBlender 1.21.1-4.1.0.8** в папке `mods`.

## Как настроить генерацию

- Размер и глубина сенотов: `configured_feature/cenote.json` (`radius`, `depth`, `water_fraction`).
- Частота сенотов: `placed_feature/cenote.json` (`rarity_filter.chance` — больше число, реже ямы).
- Высота и количество деревьев: `configured_feature/swamp_tree.json` и `placed_feature/swamp_tree.json`.
- Где появляется биом на карте: `DeepBogRegion.java` (диапазоны `temperature`, `humidity`, `erosion` и т. д.)
  и вес региона в `DeepWaater.java`.

## Статус

Версия `0.1.0`. Это первая рабочая заготовка: код написан по API NeoForge 1.21.1 и TerraBlender 1.21.1-4.1.0.8,
но ещё не проверен в игре. Перед первым запуском выполните `./gradlew build`, потом `./gradlew runClient`
и найдите биом командой `/locate biome deepwaater:deep_bog`.

Идеи для следующих шагов: свои блоки (болотная древесина, ил), мобы или предметы, пещеры внутри сенотов,
ветровой шум и туман над водой.
