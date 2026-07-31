# **CasinoRoulette** — Free Modern Casino Plugin for Minecraft! 🎰💎

Bring the atmosphere of a real casino to your Minecraft server with **CasinoRoulette** — a configurable, GUI-driven casino plugin featuring roulette, slots, wheels, statistics, leaderboards, and flexible economy support.

Create an engaging casino experience for your players with animated menus, configurable rewards, multiple game modes, and support for modern Bukkit-based server software.

> ⚠️ **DISCLAIMER**  
> CasinoRoulette is made for **virtual in-game entertainment only**.  
> The project does not promote, encourage, or support real-money gambling. Never gamble with real money.

## 🚀 **Why CasinoRoulette?**

- 🎰 **Multiple casino games** — Roulette, Slot Machine, Crash Game, Fortune Wheel, and Daily Wheel
- 🖥️ **Modern inventory GUIs** — Interactive menus, animations, sounds, and configurable items
- 💰 **Flexible economy support** — Vault economy, PlayerPoints, or built-in internal economy
- ⚙️ **Highly configurable** — Bet limits, payouts, multipliers, rewards, menus, messages, and sounds
- 📊 **Player statistics & leaderboards** — Track activity, winnings, games, and rankings
- 🔌 **PlaceholderAPI support** — Display casino data in scoreboards, holograms, chat, and more
- 🌍 **Multi-language support** — English, Russian, German, French, Polish, Portuguese (Brazil), and Turkish
- 🛡️ **Permissions & limits** — Control access to games, leaderboards, stats, and betting limits
- ⚡ **Folia support** — Region-aware scheduling for modern high-performance servers

---

## 🎲 **Available Games**

### 🔴 **Roulette** — The Classic Casino Experience
- Interactive roulette GUI with multiple betting options
- Inside and outside bets
- Configurable bet limits and payouts
- Animated game flow and visual feedback
- Player balance protection before every bet

### 🎰 **Slot Machine** — Spin to Win!
- Animated slot-machine gameplay
- Configurable symbols, rewards, and chances
- Sounds and visual effects
- Flexible economy integration

### ✈️ **Crash Game** — High Risk, High Reward
- Watch the multiplier increase
- Cash out before the crash
- Fast-paced and strategic casino gameplay
- Configurable game behavior and rewards

### 🎡 **Fortune Wheel**
- Choose a color, select a bet, and spin the wheel
- Configurable colors, limits, and payouts
- Animated GUI experience

### 🎁 **Daily Fortune Wheel**
- Daily rewards for active players
- Configurable prizes and cooldowns
- Great for improving player retention

---

## ⚡ **Platform & Version Support**

| Platform | Status |
|---|---|
| Spigot | ✅ Supported |
| Paper | ✅ Supported |
| Purpur | ✅ Supported |
| Bukkit-based forks | ✅ Supported |
| Folia | ✅ Supported |
| Sponge / SpongeVanilla | ✅ Supported |

**Minecraft versions:** `1.16.5 – 1.21.x`  
**To run on version 1.16.5, specify the launch flag `DPaper.IgnoreJavaVersion=true`**  
**Java requirement:** Java 21+ for Bukkit-based servers.  

### ⚡ Folia Support

CasinoRoulette includes a **Folia-aware scheduler system**.

Player-related actions, GUI operations, animations, delayed tasks, and region-sensitive work are routed through Folia-compatible schedulers. This makes the plugin suitable for region-threaded servers without relying only on Bukkit’s traditional global main thread.

### ⚠️ Sponge Status

Sponge support is currently **experimental**.

The plugin can be detected and launched by **Sponge / SpongeVanilla**, and its data/configuration directory is created correctly. However, the Sponge implementation is **not fully playable yet**: commands, GUI interaction, and inventory clicks are still under development.

> **Do not use Sponge support on a production server yet.**  
> Full Sponge compatibility is planned for future updates.

---

## 📦 **Dependencies**

| Plugin / Service | Status | Purpose |
|---|---:|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Optional | Economy bridge for Vault-compatible economy plugins |
| Vault-compatible economy plugin | Optional | Uses your server balance system |
| PlayerPoints | Optional | Alternative point-based savings for Folia |
| PlaceholderAPI | Optional | Casino placeholders for scoreboards, holograms, and chat |
| Internal Economy | Built-in | Fallback economy when no external provider is installed |

---

## 🌍 **Available Languages**

- 🇬🇧 English
- 🇷🇺 Russian
- 🇩🇪 German
- 🇫🇷 French
- 🇵🇱 Polish
- 🇧🇷 Portuguese (Brazil)
- 🇹🇷 Turkish

Language files are easy to edit, translate, and customize.

---

## ⚙️ **Configuration & Customization**

Configure nearly every part of your casino:

- Minimum and maximum bet amounts
- Game payouts and multipliers
- Wheel rewards and daily rewards
- Economy mode and currency names
- GUI titles, items, lore, and layouts
- Messages and sounds
- Game availability
- Player betting limits
- Leaderboard categories
- Language files

---

## 📋 **Commands**

| Command | Description | Permission |
|---|---|---|
| `/casino` | Open the main casino menu | Default |
| `/casino wheel` | Open the Fortune Wheel | `casino.wheel` |
| `/casino daily` | Open the Daily Wheel | `casino.dailywheel` |
| `/casino top` | Open the leaderboard | `casino.leaderboard` |
| `/casino leaderboard` | Open the leaderboard | `casino.leaderboard` |
| `/casino stats` | View casino statistics | `casino.stats` |
| `/casino reload` | Reload plugin configuration | `casino.reload` |
| `/dailywheel` | Open the Daily Fortune Wheel | `casino.dailywheel` |

---

## 🔐 **Permissions**

| Permission | Description | Default |
|---|---|---|
| `casino.admin` | Full administrative access | OP |
| `casino.reload` | Reload configuration and messages | OP |
| `casino.update.notify` | Receive update notifications | OP |
| `casino.limit.vip` | Increased betting-position limit | false |
| `casino.limit.admin` | Maximum betting-position limit | false |
| `casino.leaderboard` | Access casino leaderboards | true |
| `casino.stats` | View casino statistics | true |
| `casino.wheel` | Use the Fortune Wheel | true |
| `casino.dailywheel` | Use the Daily Wheel | true |
| `casino.placeholders` | PlaceholderAPI integration | true |

---

## 💡 **Development & Support**

CasinoRoulette is actively being improved.

- Found a bug or have a suggestion? → Join our [Discord](https://discord.gg/72mzBTckKC)
- Want to help translate the plugin? → Contact us on Discord
- Want to contribute? → Visit [GitHub](https://github.com/Stepanyaa/CasinoRoulette)

**License:** MIT

---

### ⚠️ **Important Reminder**

CasinoRoulette is designed only for **Minecraft server entertainment**.  
The developer does not encourage real-life gambling. Please play responsibly.

**Enjoy responsibly and have fun!** 🎉

---

<details>
<summary><strong>Русская версия (Russian version) ▼</strong></summary>

# **CasinoRoulette** — современный бесплатный плагин казино для Minecraft! 🎰💎

Добавьте атмосферу настоящего казино на свой Minecraft-сервер с помощью **CasinoRoulette** — настраиваемого казино-плагина с GUI-меню, рулеткой, слот-машиной, колёсами удачи, статистикой, топом игроков и гибкой системой экономики.

Создайте для игроков увлекательное игровое казино с анимациями, звуками, наградами, несколькими играми и поддержкой современных Bukkit-серверов.

> ⚠️ **ВАЖНОЕ ПРЕДУПРЕЖДЕНИЕ**  
> CasinoRoulette создан только для **виртуального развлечения внутри Minecraft**.  
> Проект не поощряет и не поддерживает азартные игры на реальные деньги. Никогда не играйте на реальные деньги.

## 🚀 **Почему CasinoRoulette?**

- 🎰 **Несколько казино-игр** — рулетка, слот-машина, Crash Game, колесо удачи и ежедневное колесо
- 🖥️ **Современные GUI-меню** — интерактивные интерфейсы, анимации, звуки и настраиваемые предметы
- 💰 **Гибкая поддержка экономики** — Vault, PlayerPoints или встроенная внутренняя экономика
- ⚙️ **Глубокая настройка** — лимиты ставок, выплаты, множители, награды, меню, сообщения и звуки
- 📊 **Статистика и топ игроков** — отслеживание активности, выигрышей, игр и рейтингов
- 🔌 **Поддержка PlaceholderAPI** — вывод данных казино в скорбордах, голограммах, чате и других плагинах
- 🌍 **Поддержка языков** — English, Русский, Deutsch, Français, Polski, Português (Brasil) и Türkçe
- 🛡️ **Права и лимиты** — настройка доступа к играм, статистике, топам и ставкам
- ⚡ **Поддержка Folia** — регионально-безопасная система задач для современных серверов

---

## 🎲 **Доступные игры**

### 🔴 **Рулетка** — классическое казино
- Интерактивное GUI-меню рулетки
- Внутренние и внешние ставки
- Настраиваемые лимиты и выплаты
- Анимации и визуальные эффекты
- Проверка баланса перед ставкой

### 🎰 **Слот-машина** — крутите и выигрывайте!
- Анимированный игровой автомат
- Настраиваемые символы, награды и шансы
- Звуки и эффекты
- Работа с разными системами экономики

### ✈️ **Crash Game** — высокий риск, высокая награда
- Следите за ростом множителя
- Забирайте выигрыш до краха
- Быстрый и стратегический игровой процесс
- Настраиваемые параметры игры и награды

### 🎡 **Колесо удачи**
- Выберите цвет, сделайте ставку и крутите колесо
- Настраиваемые цвета, лимиты и выплаты
- Анимированное GUI-меню

### 🎁 **Ежедневное колесо удачи**
- Ежедневные награды для игроков
- Настраиваемые призы и время ожидания
- Отличный способ повысить активность игроков

---

## ⚡ **Поддержка платформ и версий**

| Платформа | Статус |
|---|---|
| Spigot | ✅ Поддерживается |
| Paper | ✅ Поддерживается |
| Purpur | ✅ Поддерживается |
| Bukkit-ядра и форки | ✅ Поддерживаются |
| Folia | ✅ Поддерживается |
| Sponge / SpongeVanilla | ✅ Поддерживается |

**Версии Minecraft:** `1.16.5 – 1.21.x`  
**Для запуска в версии 1.16.5 укажите флаг запуска `DPaper.IgnoreJavaVersion=true`**  
**Требование Java:** Java 11+ для Bukkit-серверов.

### ⚡ Поддержка Folia

CasinoRoulette использует **Folia-совместимую систему планировщиков**.

Действия игроков, GUI-меню, анимации, задержки и операции, связанные с регионами, выполняются с учётом региональной многопоточности Folia. Это делает плагин подходящим для современных высокопроизводительных серверов.

### ⚠️ Статус Sponge

Поддержка **Sponge / SpongeVanilla** сейчас экспериментальная.

Плагин определяется и запускается на Sponge, а папка данных и конфигурации создаётся корректно. Однако полноценная работа ещё не завершена: команды, взаимодействие с GUI и обработка кликов в меню всё ещё дорабатываются.

> **Не используйте Sponge-версию на основном сервере.**  
> Полная поддержка Sponge планируется в будущих обновлениях.

---

## 📦 **Зависимости**

| Плагин / Сервис | Статус | Назначение |
|---|---:|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Необязательно | Подключение Vault-совместимой экономики |
| Vault-совместимая экономика | Необязательно | Использование баланса вашего сервера |
| PlayerPoints | Необязательно | Альтернативная экономика на очках для Folia |
| PlaceholderAPI | Необязательно | Плейсхолдеры для скорбордов, голограмм и чата |
| Внутренняя экономика | Встроено | Резервная экономика без внешних зависимостей |

---

## 🌍 **Доступные языки**

- 🇬🇧 English
- 🇷🇺 Русский
- 🇩🇪 Deutsch
- 🇫🇷 Français
- 🇵🇱 Polski
- 🇧🇷 Português (Brasil)
- 🇹🇷 Türkçe

Языковые файлы легко редактировать и переводить.

---

## ⚙️ **Настройка**

Вы можете настроить практически всё:

- Минимальные и максимальные ставки
- Выплаты и множители игр
- Награды колёс удачи
- Экономику и названия валют
- Предметы, названия, lore и расположение элементов GUI
- Сообщения и звуки
- Доступность отдельных игр
- Лимиты игроков
- Категории топа
- Языковые файлы

---

## 📋 **Команды**

| Команда | Описание | Право |
|---|---|---|
| `/casino` | Открыть главное меню казино | По умолчанию |
| `/casino wheel` | Открыть колесо удачи | `casino.wheel` |
| `/casino daily` | Открыть ежедневное колесо | `casino.dailywheel` |
| `/casino top` | Открыть топ игроков | `casino.leaderboard` |
| `/casino leaderboard` | Открыть топ игроков | `casino.leaderboard` |
| `/casino stats` | Просмотреть статистику казино | `casino.stats` |
| `/casino reload` | Перезагрузить конфигурацию | `casino.reload` |
| `/dailywheel` | Открыть ежедневное колесо | `casino.dailywheel` |

---

## 🔐 **Права**

| Право | Описание | По умолчанию |
|---|---|---|
| `casino.admin` | Полный доступ к администрированию | OP |
| `casino.reload` | Перезагрузка конфигурации и сообщений | OP |
| `casino.update.notify` | Уведомления об обновлениях | OP |
| `casino.limit.vip` | Увеличенный лимит игровых позиций | false |
| `casino.limit.admin` | Максимальный лимит игровых позиций | false |
| `casino.leaderboard` | Доступ к топу игроков | true |
| `casino.stats` | Доступ к статистике | true |
| `casino.wheel` | Доступ к колесу удачи | true |
| `casino.dailywheel` | Доступ к ежедневному колесу | true |
| `casino.placeholders` | Интеграция с PlaceholderAPI | true |

---

## 💡 **Разработка и поддержка**

CasinoRoulette активно развивается.

- Нашли ошибку или есть идея? → [Discord](https://discord.gg/72mzBTckKC)
- Хотите помочь с переводом? → Напишите в Discord
- Хотите внести вклад в разработку? → [GitHub](https://github.com/Stepanyaa/CasinoRoulette)

**Лицензия:** MIT

---

### ⚠️ **Напоминание**

CasinoRoulette предназначен только для **развлечения на Minecraft-сервере**.  
Разработчик не поощряет азартные игры в реальной жизни.

**Играйте ответственно и получайте удовольствие!** 🎉

</details>
