# Желтый шарик

Помощник для Minecraft, который появляется из коробки, разговаривает с игроком и превращается в монстра при плохом обращении. Подробности описаны в [docs/features.md](docs/features.md).

## Установка на macOS

Нужны **Minecraft Java Edition 26.3**, **Fabric Loader 0.20.1** и **Fabric API 0.166.2+26.3**. Инструкция рассчитана на Minecraft Launcher на Mac с Intel или Apple Silicon.

1. Установи **JDK 25**. Для Apple Silicon выбирай `aarch64` / `arm64`, для Intel — `x64` / `x86_64`. Java понадобится для установщика Fabric и сборки мода. [Установка Java на macOS](https://docs.fabricmc.net/players/installing-java/macos)
2. Один раз запусти Minecraft **26.3**, затем закрой игру и Launcher. Скачай установщик Fabric **Universal/.JAR**, выбери **Client**, Minecraft **26.3**, Loader **0.20.1** и создание профиля. После установки один раз запусти профиль Fabric и закрой игру. [Установщик Fabric](https://fabricmc.net/use/installer/)
3. В Launcher открой **Установки → профиль Fabric → Изменить → Папка игры**. Найди в этой папке каталог `mods` или создай его. По умолчанию это `~/Library/Application Support/minecraft/mods` — путь можно открыть в Finder через **⌘⇧G**. Если у профиля указана отдельная папка игры, используй её каталог `mods`. [Установка модов Fabric](https://docs.fabricmc.net/players/installing-mods)
4. Помести в `mods` файлы `fabric-api-0.166.2+26.3.jar` и `echo-lure-1.0.14+mc26.3.jar`. JAR мода находится в `build/` после сборки по инструкции ниже. При обновлении убери предыдущую версию этого мода из `mods`. [Скачать Fabric API для 26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3)
5. Запусти профиль Fabric для **26.3**, открой мир и введи `/yellow status`. В начале новой истории рядом появится коробка с Желтым шариком.

Если команда недоступна, проверь профиль, версию Minecraft и папку `mods`. При успешной загрузке в `logs/latest.log` внутри папки игры будет строка `echo_lure 1.0.14`.

## Бесплатная языковая модель

Для свободного разговора установи **Ollama** и скачай **Gemma 3 4B**. Ollama поддерживает macOS **14 или новее**, Apple Silicon и Intel. На Intel модель работает на CPU. [Ollama для macOS](https://docs.ollama.com/macos)

```bash
open -a Ollama
ollama pull gemma3:4b
```

Держи Ollama запущенным во время игры. Модель работает локально, без API-ключа и оплаты запросов. Первая загрузка занимает около 3,4 ГБ. Для одиночной игры Ollama и Minecraft запускаются на одном Mac. [Gemma 3 4B](https://ollama.com/library/gemma3:4b)

## Начало игры

Для свободного разговора напиши в игровом чате `verity: привет, расскажи короткую историю`. Также работают `Желтый шарик:`, `yellow:` и `/yellow ask <вопрос>`. Аметистовые осколки повышают доверие, регулярные удары примерно за 3–4 минуты доводят его до ярости.

Попроси `verity: дай 8 алмазов` или `verity: подари алмазную кирку` — он может выдать предметы или отказать по настроению. Эта возможность работает без Ollama; между просьбами о предметах — 30 секунд.

**ПКМ по шарику** — взять, следующий **ПКМ** — бросить в направлении взгляда. Также работают **G** и `/yellow throw`. Можно бросить в лаву: он выживет, разозлится и вернётся.

**Shift + ПКМ по шарику** — убрать в свободную ячейку инвентаря. Возьми полученный предмет в руку и нажми **ПКМ в воздух**, чтобы выпустить. В инвентаре история и злость на паузе; доверие сохраняется.

- `/yellow status` — состояние и злость.
- `/yellow stop` — выключить историю.
- `/yellow start` — после остановки начать историю заново.

## Сборка из исходников

Установи Python 3 и JDK 25, выполни шаги установки Minecraft и Fabric и скачай Fabric API. В терминале перейди в корень репозитория и выполни команды ниже. [Python для macOS](https://www.python.org/downloads/macos/)

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
python3 build.py --fabric-api "$HOME/Library/Application Support/minecraft/mods/fabric-api-0.166.2+26.3.jar"
```

Если Fabric API лежит в другом месте, подставь его полный путь в `--fabric-api`. Для другой папки установки Launcher добавь `--minecraft-dir "/путь/к/minecraft"`. Папка установки содержит `versions` и `libraries` и может отличаться от папки игры профиля. Результат — `build/echo-lure-1.0.14+mc26.3.jar`.
