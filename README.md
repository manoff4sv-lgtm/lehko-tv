<p align="center"><img src="docs/icon.png" width="96" alt="Lehko"></p>

<h1 align="center">Lehko</h1>

<p align="center"><b>Легкий головний екран для слабких телевізорів на Android TV і Google TV.</b><br>
Без реклами, з погодою і графіком відключень світла.</p>

<p align="center"><a href="#english">English below</a></p>

---

Стандартний екран Google TV на телевізорах з 1 ГБ памʼяті гальмує і показує рекламу. Lehko замінює його простим
швидким екраном: годинник, погода, графік відключень вашого будинку, курси валют, улюблені додатки і входи HDMI.
Без реклами і без важких ефектів: сам Lehko займає кілька десятків мегабайт.

## Що вміє

- **Спокійний екран.** Годинник стоїть у кільці на 24 години, і це кільце і є графік світла на сьогодні:
  помаранчеві дуги означають, що світла не буде, точка показує «зараз». Поруч три короткі рядки: світло (є чи нема
  і до котрої), погода, курси. Натисніть «вгору» з улюблених, і відкриються подробиці: погода по годинах, схід і
  захід сонця, повні курси, графік на завтра.
- **Улюблені додатки** з банерами, як на звичайному Android TV. Затисніть OK на додатку, щоб посунути, прибрати або видалити.
- **Фон під додаток.** Коли вибираєте улюблений додаток, фон плавно набуває темного відтінку його банера. Увечері,
  після заходу сонця, відтінок ще темніший.
- **Назва вибраного додатка** під рядом улюблених: на банерах її видно не завжди.
- **Погода** від Open-Meteo: зараз і «дощ з 17:00», а в подробицях мінімум і максимум, наступні 5 годин, схід і
  захід сонця. Працює без ключів і показує останній прогноз навіть без інтернету. Якщо оновити не вдається, а
  прогнозу вже понад 3 години, поруч зʼявляється «Не оновлювалося з 14:05»; заставка-годинник такий прогноз не показує.
- **Курси:** долар і євро до гривні: продаж у Монобанку (або офіційний курс НБУ, якщо Монобанк не відповідає), у
  подробицях купівля і продаж. Вмикаються в налаштуваннях; останні значення видно й без інтернету.
- **Графік відключень світла**: зараз є чи нема і до котрої, коли відключать або ввімкнуть, кільце на сьогодні,
  смуга на завтра в подробицях. Якщо графік не вдається оновити понад 2 години (скажімо, під час відключення немає
  інтернету), рядок пише, з котрої він години: «Не оновлювалося з 09:00». Так само в каміні.
  - Київ (місто): група за вашою адресою (з 2026 року в Києві 60 груп по будинках), дані YASNO, запасне джерело ДТЕК.
  - Київська область та Одеська область: дані ДТЕК.
  - Дніпропетровщина (ДнЕМ і ЦЕК): дані YASNO, група за адресою.
- **Входи HDMI і ефірне ТБ** в один клік, налаштування телевізора, усі додатки: круглі кнопки внизу, назва
  зʼявляється під рядом улюблених.
- **Вимкнення зайвих додатків**, щоб звільнити памʼять. Нічого не видаляється, усе вмикається назад однією кнопкою.
- **Памʼять.** Кнопка «Памʼять» показує, скільки вільно. Натискання закриває додатки, які Android тримає у фоні
  (через режим налагодження; без нього звичайним способом Android, який працює до Android 13), і пише, скільки
  звільнилося. Автоочищення робить те саме автоматично, коли памʼяті мало (його можна вимкнути). Закриті додатки наступного
  разу відкриваються трохи довше. У налаштуваннях видно, скільки займає Lehko і скільки займав стандартний лаунчер.
- **Камін.** Якщо 5 хвилин нічого не натискати на головному екрані, запалюється камін: цегла, поліна, живий вогонь,
  тьмяний годинник і стан світла з графіка. Кнопки гучності й відтворення працюють як завжди, будь-яка інша повертає
  назад. Можна поставити 10 чи 15 хвилин або вимкнути; тихе потріскування вмикається окремо і не звучить поверх
  музики. Екран камін не тримає: звичайна заставка і вимкнення телевізора працюють як завжди. Тому якщо таймер
  заставки (або сну) телевізора не довший за час каміна, першою вмикається заставка телевізора, а камін пропускається.
  Тоді виберіть заставкою камін Lehko; рядок «Камін у простої» в налаштуваннях про це підкаже.
- **Заставка** на вибір: стандартна, годинник Lehko (темний, повільно переміщується) або камін Lehko.
- **Безпечно**: стандартний лаунчер лише вимикається, не видаляється. Кнопка «Повернути» поверне все як було.
  Якщо екран Lehko тричі впаде за 5 хвилин, телевізор сам повернеться до стандартного екрана. На Android 11 і новіших
  рахуються також зависання і збої нативного коду (наприклад, графічного драйвера).
- **Доступність.** Текст контрастний за WCAG AA навіть на кольоровому фоні, фокус завжди обведений білою рамкою,
  графік відключень читається без розрізнення червоного й зеленого (світло є: тонка лінія, нема: товста дуга,
  можливо: пунктир). TalkBack зачитує погоду, графік і сповіщення. Якщо в налаштуваннях телевізора вимкнено
  анімації, камін стоїть нерухомо.
- Українська, англійська і російська мови (як у системі телевізора).

## Встановлення

### 1. Увімкніть режим налагодження на телевізорі

Google TV і Android TV дозволяють змінити головний екран лише через режим налагодження. Lehko користується ним
прямо на телевізорі, компʼютер не потрібен.

1. **Налаштування → Система → Про пристрій** → 7 разів натисніть **«Збірка ОС Android TV»**.
2. **Налаштування → Система → Параметри розробника** → увімкніть **«Налагодження USB»**.

### 2. Встановіть Lehko

**Без компʼютера.** Встановіть з Google Play додаток **Downloader** (від AFTVnews), відкрийте його і введіть адресу:

```
https://github.com/manoff4sv-lgtm/lehko-tv/releases/latest/download/Lehko.apk
```

Дозвольте Downloader встановлювати додатки, коли телевізор спитає.

**З компʼютера** в тій самій мережі Wi-Fi (потрібен [adb](https://developer.android.com/tools/releases/platform-tools)):

```sh
scripts/install.sh 192.168.1.50      # macOS / Linux
scripts\install.bat 192.168.1.50     # Windows
```

IP телевізора є в **Налаштування → Мережа та інтернет → ваша мережа**.

### 3. Увімкніть

Відкрийте **Lehko** у списку додатків → **Головний екран** → **Увімкнути**. На екрані зʼявиться вікно
«Дозволити налагодження USB?»: позначте **«Завжди дозволяти»** і натисніть **OK**. Готово.

Галочка «Також вимкнути стандартний лаунчер» звільняє найбільше памʼяті, але за замовчуванням вимкнена. Спершу
спробуйте без неї. Якщо кнопка Home все одно відкриває стандартний екран, вимкніть лаунчер пізніше: Lehko →
Головний екран → **Також вимкнути стандартний лаунчер**. Там само його можна знову ввімкнути (**Увімкнути стандартний
лаунчер знову**), а Lehko лишиться головним екраном. Повертати для цього стандартний екран не треба: це ще й увімкнуло б
назад усі зайві додатки і скинуло заставку. **Поки лаунчер вимкнений, завжди повертайте стандартний екран перед
видаленням Lehko.**

> Не вимикайте «Налагодження USB» після встановлення: через нього Lehko перевіряє себе після оновлень телевізора.
> Дозволяйте налагодження лише тоді, коли самі щойно натиснули кнопку в Lehko.

**Якщо телевізор забув дозвіл** (Lehko про це напише): Lehko → Головний екран → **Дозволити налагодження знову**,
і у вікні позначте «Завжди дозволяти».

**Якщо порт закривається після перезавантаження.** Деякі телевізори відкривають порт налагодження лише командою
`adb tcpip 5555` з компʼютера і закривають його після кожного перезавантаження. Тоді виконуйте її після кожного
перезавантаження телевізора або ввімкніть «Налагодження по мережі», якщо воно є. Lehko і без цього лишається
головним екраном, але не може перевірити себе після оновлень телевізора, а кнопки, яким потрібне налагодження,
скажуть, що порт закритий.

## Як повернути все назад

- **На телевізорі:** Lehko → Головний екран → **Повернути стандартний екран** → **Повернути**. Усе, що вимикав
  Lehko, знову ввімкнеться: і лаунчер, і додатки з екрана «Зайві додатки».
- **Стандартний екран уже працює, а зайві додатки ще вимкнені:** Lehko → **Увімкнути додатки назад** (цей рядок
  зʼявляється в налаштуваннях сам, поки такі додатки є).
- **З компʼютера**, навіть якщо Lehko вже видалено: `scripts/revert.sh 192.168.1.50` (або `revert.bat`). Додайте
  `--uninstall`, щоб заразом видалити Lehko. Якщо Lehko лишився, він помітить такий відкат, щойно наступного разу
  звернеться до телевізора, і сам головним екраном знову не стане: ні після перезавантаження, ні після оновлення.

**Перед видаленням Lehko спершу натисніть «Повернути»** (або «Увімкнути додатки назад», якщо стандартний екран уже
працює), інакше телевізор може залишитися без головного екрана, а вимкнені додатки так і лишаться вимкненими.

## Що саме змінюється

- Lehko стає головним екраном (`cmd package set-home-activity`).
- Якщо галочка увімкнена, вимикаються (`pm disable-user`, не видаляються): стандартний лаунчер; на Google TV ще
  екран відновлення з майстра налаштування (інакше він займає місце лаунчера); на старому Android TV ще сервіс рекомендацій.
- Додатки, які зберегли вашу мережу Wi-Fi, ніколи не вимикаються: Android тоді забув би мережу. Системні служби,
  магазин, клавіатура, пульт і входи телевізора в захищеному списку. На екрані «Зайві додатки» не пропонуються ще
  головний екран, вибрана клавіатура, служби доступності, заставка, ефірне ТБ і плеєр HDMI.
- Заставка змінюється, лише коли ви самі вибираєте заставку Lehko. «Повернути» і скрипт відкату ставлять стандартну
  назад, тільки поки стоїть заставка Lehko: вибрану потім у налаштуваннях телевізора вони не чіпають.
- На Android 11+ вимикається тайм-аут дозволу налагодження (як пункт «Disable adb authorization timeout» у Параметрах
  розробника), інакше через 7 днів телевізор забуває дозвіл і страховка Lehko не спрацює. «Повернути» відновлює як було.
- Список змін Lehko також пише в `/data/local/tmp/lehko_state` на телевізорі: його читають скрипт відкату з
  компʼютера і сам Lehko після перевстановлення.
- Якщо екран Lehko тричі впаде за 5 хвилин, вмикається безпечний режим: Lehko нічого не малює, а одразу повертає
  стандартний екран. Якщо повернути не вдалося, екран пише чому (наприклад, порт налагодження закритий) і має кнопки
  «Спробувати ще раз», до налаштувань телевізора і до налаштувань Lehko.

## Приватність

Без акаунтів, аналітики і реклами. Lehko звертається лише до:
- [Open-Meteo](https://open-meteo.com) за погодою (координати вибраного міста);
- [YASNO](https://yasno.ua) і [outage-data-ua](https://github.com/Baskerville42/outage-data-ua) (дзеркало графіків ДТЕК на GitHub) за графіками відключень, якщо ви їх увімкнули;
- [Монобанк](https://api.monobank.ua/docs/) і [НБУ](https://bank.gov.ua/ua/open-data/api-dev) за курсами валют, якщо вони увімкнені.

Ваша адреса для пошуку групи надсилається лише в YASNO і зберігається тільки на телевізорі.

## Збірка

```sh
./gradlew assembleRelease     # APK: app/build/outputs/apk/release/
./gradlew test
```

Без Android Studio: `tools/quick-build.sh` і `tools/quick-test.sh` (aapt2 + javac + dx). Тести ADB і перемикання
головного екрана працюють проти `tools/fake_adbd.py --tv`, симулятора Google TV (Wi-Fi мережі, роль HOME, пакети).
Рядки інтерфейсу: `tools/strings/strings.py` генерує `values`, `values-uk`, `values-ru`.

Код: Java без бібліотек (жодних AndroidX), щоб додаток важив ~200 КБ і не займав памʼять.

## Подяки

- [Free My TV](https://github.com/simbasjungles/free-my-tv): ідея і підхід із вбудованим ADB-клієнтом.
- [Open-Meteo](https://open-meteo.com), [YASNO](https://yasno.ua), [ДТЕК](https://www.dtek-kem.com.ua/ua/shutdowns),
  [outage-data-ua](https://github.com/Baskerville42/outage-data-ua), Монобанк, НБУ.
- Вогонь у каміні: класичний алгоритм «Doom fire» (Fabien Sanglard описав його у статті «How DOOM fire was done»).

Ліцензія [MIT](LICENSE).

---

<a id="english"></a>

## English

**Lehko** ("light" in Ukrainian) is a light, ad-free home screen for weak Android TV / Google TV sets (made for a
1 GB Xiaomi TV A, works on any Android TV 8+). The clock sits in a 24-hour ring that is today's power outage
schedule for your building (in Ukraine: Kyiv city by address via YASNO, Kyiv region and Odesa region via DTEK,
Dnipro region via YASNO); beside it, short lines for power, the weather (Open-Meteo) and rates; below, your favorite
apps with banners and round buttons for HDMI inputs, Live TV and settings. Up from the favorites opens the details:
the next 5 hours, sunrise and sunset, full rates, tomorrow's schedule.

Also: the background takes on a dark tint of the focused app's banner; the rates line beside the clock gives the
dollar and euro in hryvnias (Monobank, or the NBU), with buy and sell in the details; a "Memory" button that shows
free memory and closes cached background apps (through the TV's debugging, or Android's own way up to Android 13),
with optional automatic cleaning when memory runs low; and a fireplace (bricks, logs, a "Doom fire", a dim clock and
the outage status, optional quiet crackle that stays silent over music) after 5 idle minutes on the home screen, also
available as a screensaver. Volume and playback keys keep working under the fire; any other key goes back. The
fireplace doesn't keep the screen on, so when the TV's own screensaver (or sleep) timer is no longer than the
fireplace time, the TV's screensaver comes first and the idle fireplace is skipped: pick the Lehko fireplace as the
screensaver instead (Settings says so).

Offline, the last forecast and schedule stay up. When updating fails or there is no network, a forecast over 3 hours
old or a schedule over 2 hours old says "No update since 09:00" (on the fireplace too), and the clock screensaver
leaves an old forecast out.

Accessibility: text passes WCAG AA contrast even on the tinted background, focus always has a white outline, the
outage ring and bar read without telling red from green (on: thin line, off: thick arc, possible: dashed),
TalkBack reads the weather, the schedule and notices, and with animations turned off in the TV's settings the
fireplace stands still.

**Install:** turn on developer options and USB debugging on the TV, install
[Lehko.apk](https://github.com/manoff4sv-lgtm/lehko-tv/releases/latest/download/Lehko.apk) (with the Downloader app,
or `scripts/install.sh TV_IP` from a computer), open Lehko → Home screen → Turn on, and allow debugging on the TV.
Lehko talks to the TV's own debugging port (127.0.0.1:5555) to make itself the home screen and, optionally, turn
off the stock launcher. Nothing is uninstalled. Try it without "Also turn off the stock launcher" first. If Home
still opens the stock screen, turn the launcher off later from the same row (Lehko → Home screen), where it can also
be turned back on while Lehko stays the home screen. No need to switch back for that, which would also turn the
unneeded apps back on and reset the screensaver.

Keep USB debugging on: Lehko checks itself through it after TV updates. If the TV forgets the permission (Lehko says
so), use Lehko → Home screen → Allow debugging again and tick "Always allow". Some TVs open the debugging port only
after `adb tcpip 5555` from a computer and close it on every restart: run it again after each restart, or turn on
Network debugging if the TV has it. Without it Lehko stays the home screen but can't check itself after TV updates.

**Go back:** Lehko → Home screen → Bring back the TV's home screen. Every app Lehko turned off comes back on, the
stock launcher and the unneeded apps alike; Lehko's screensaver gives way to the TV's own (one you picked in the
TV's settings since then stays). If the TV's own home screen is in use already but apps turned off on Unneeded apps
are still off: Lehko → Turn apps back on. Do one of these before uninstalling Lehko. From a computer:
`scripts/revert.sh TV_IP` (works even if Lehko is gone). A Lehko still installed accepts that revert the next time it
talks to the TV and doesn't take the home screen back, not after a restart and not after an update. If Lehko's home
screen crashes 3 times in 5 minutes (on Android 11+ hangs and native crashes count too), the original comes back by
itself; if that fails, the safe screen says why and has a button to Lehko settings.

**Privacy:** no accounts, analytics or ads; only Open-Meteo, YASNO and the outage-data-ua mirror are contacted, plus
Monobank and the NBU when the rates line is on.

**Build:** `./gradlew assembleRelease`. Plain Java, no libraries. MIT licensed. Inspired by
[Free My TV](https://github.com/simbasjungles/free-my-tv).
