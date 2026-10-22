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

- **Улюблені додатки** з банерами, як на звичайному Android TV. Затисніть OK на додатку, щоб посунути, прибрати або видалити.
- **Фон під додаток.** Коли вибираєте улюблений додаток, фон плавно набуває темного відтінку його банера. Увечері,
  після заходу сонця, відтінок ще темніший.
- **Назва вибраного додатка** під рядом улюблених: на банерах її видно не завжди.
- **Погода** від Open-Meteo: зараз, мінімум і максимум, наступні 5 годин, схід і захід сонця, «дощ з 17:00». Працює
  без ключів і показує останній прогноз навіть без інтернету.
- **Курси одним рядком під датою:** долар і євро до гривні (купівля / продаж Монобанку, або офіційний курс НБУ, якщо
  Монобанк не відповідає), біткоїн і ефір у доларах з Binance зі зміною за добу. Валюти і криптовалюти вмикаються
  окремо в налаштуваннях; останні значення видно й без інтернету.
- **Графік відключень світла**: зараз є чи нема, коли відключать або ввімкнуть, смуга на сьогодні й завтра.
  - Київ (місто): група за вашою адресою (з 2026 року в Києві 60 груп по будинках), дані YASNO, запасне джерело ДТЕК.
  - Київська область та Одеська область: дані ДТЕК.
  - Дніпропетровщина (ДнЕМ і ЦЕК): дані YASNO, група за адресою.
- **Входи HDMI і ефірне ТБ** в один клік, налаштування телевізора, усі додатки.
- **Вимкнення зайвих додатків**, щоб звільнити памʼять. Нічого не видаляється, усе вмикається назад однією кнопкою.
- **Памʼять.** Плитка «Памʼять» показує, скільки вільно. Натискання закриває додатки, які Android тримає у фоні
  (через режим налагодження; без нього звичайним способом Android, який працює до Android 13), і пише, скільки
  звільнилося. Автоочищення робить те саме автоматично, коли памʼяті мало (його можна вимкнути). Закриті додатки наступного
  разу відкриваються трохи довше. У налаштуваннях видно, скільки займає Lehko і скільки займав стандартний лаунчер.
- **Камін.** Якщо 5 хвилин нічого не натискати на головному екрані, запалюється камін: цегла, поліна, живий вогонь,
  тьмяний годинник і стан світла з графіка. Будь-яка кнопка повертає назад. Можна поставити 10 чи 15 хвилин або
  вимкнути; тихе потріскування вмикається окремо. Екран камін не тримає: звичайна заставка і вимкнення телевізора
  працюють як завжди.
- **Заставка** на вибір: стандартна, годинник Lehko (темний, повільно переміщується) або камін Lehko.
- **Безпечно**: стандартний лаунчер лише вимикається, не видаляється. Кнопка «Повернути» поверне все як було.
  Якщо екран Lehko тричі впаде за 5 хвилин, телевізор сам повернеться до стандартного екрана.
- **Доступність.** Текст контрастний за WCAG AA навіть на кольоровому фоні, фокус завжди обведений білою рамкою,
  графік відключень читається без розрізнення червоного й зеленого (світло є: тонка лінія, нема: суцільний блок,
  можливо: контур). З TalkBack пультом можна дійти до погоди й графіка, а сповіщення озвучуються. Якщо в
  налаштуваннях телевізора вимкнено анімації, камін стоїть нерухомо.
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
спробуйте без неї; якщо кнопка Home все одно відкриває стандартний екран, увімкніть її. **З цією галочкою завжди
повертайте стандартний екран перед видаленням Lehko.**

> Не вимикайте «Налагодження USB» після встановлення: через нього Lehko перевіряє себе після оновлень телевізора.
> Дозволяйте налагодження лише тоді, коли самі щойно натиснули кнопку в Lehko.

## Як повернути все назад

- **На телевізорі:** Lehko → Головний екран → **Повернути**. Усе, що вимикав Lehko, знову ввімкнеться.
- **З компʼютера**, навіть якщо Lehko вже видалено: `scripts/revert.sh 192.168.1.50` (або `revert.bat`). Додайте
  `--uninstall`, щоб заразом видалити Lehko.

**Перед видаленням Lehko спершу натисніть «Повернути»**, інакше телевізор може залишитися без головного екрана.

## Що саме змінюється

- Lehko стає головним екраном (`cmd package set-home-activity`).
- Якщо галочка увімкнена, вимикаються (`pm disable-user`, не видаляються): стандартний лаунчер; на Google TV ще
  екран відновлення з майстра налаштування (інакше він займає місце лаунчера); на старому Android TV ще сервіс рекомендацій.
- Додатки, які зберегли вашу мережу Wi-Fi, ніколи не вимикаються: Android тоді забув би мережу. Системні служби,
  магазин, клавіатура, пульт і входи телевізора в захищеному списку.
- На Android 11+ вимикається тайм-аут дозволу налагодження (як пункт «Disable adb authorization timeout» у Параметрах
  розробника), інакше через 7 днів телевізор забуває дозвіл і страховка Lehko не спрацює. «Повернути» відновлює як було.
- Список змін Lehko також пише в `/data/local/tmp/lehko_state` на телевізорі: його читають скрипт відкату з
  компʼютера і сам Lehko після перевстановлення.
- Якщо екран Lehko тричі впаде за 5 хвилин, вмикається безпечний режим: Lehko нічого не малює, а одразу повертає
  стандартний екран.

## Приватність

Без акаунтів, аналітики і реклами. Lehko звертається лише до:
- [Open-Meteo](https://open-meteo.com) за погодою (координати вибраного міста);
- [YASNO](https://yasno.ua) і [outage-data-ua](https://github.com/Baskerville42/outage-data-ua) (дзеркало графіків ДТЕК на GitHub) за графіками відключень, якщо ви їх увімкнули;
- [Монобанк](https://api.monobank.ua/docs/) і [НБУ](https://bank.gov.ua/ua/open-data/api-dev) за курсами валют, [Binance](https://www.binance.com) за цінами криптовалют, якщо ці рядки увімкнені.

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
  [outage-data-ua](https://github.com/Baskerville42/outage-data-ua), Монобанк, НБУ, Binance.
- Вогонь у каміні: класичний алгоритм «Doom fire» (Fabien Sanglard описав його у статті «How DOOM fire was done»).

Ліцензія [MIT](LICENSE).

---

<a id="english"></a>

## English

**Lehko** ("light" in Ukrainian) is a light, ad-free home screen for weak Android TV / Google TV sets (made for a
1 GB Xiaomi TV A, works on any Android TV 8+). It shows the clock, weather (Open-Meteo: now, the next 5 hours,
sunrise and sunset), your favorite apps with banners, HDMI inputs and, in Ukraine, the planned power outage schedule
for your building (Kyiv city by address via YASNO, Kyiv region and Odesa region via DTEK, Dnipro region via YASNO).

Also: the background takes on a dark tint of the focused app's banner; a line under the date with the dollar and
euro in hryvnias (Monobank, or the NBU) and Bitcoin and Ether (Binance); a "Memory" tile that shows free memory and
closes cached background apps (through the TV's debugging, or Android's own way up to Android 13), with optional
automatic cleaning when memory runs low; and a fireplace (bricks, logs, a "Doom fire", a dim clock and the outage
status, optional quiet crackle) after 5 idle minutes on the home screen, also available as a screensaver. The
fireplace doesn't keep the screen on.

Accessibility: text passes WCAG AA contrast even on the tinted background, focus always has a white outline, the
outage bar reads without telling red from green (on: thin line, off: solid block, possible: outline), with TalkBack
the weather and the schedule can be reached and are read out (notices too), and with animations turned off in the
TV's settings the fireplace stands still.

**Install:** turn on developer options and USB debugging on the TV, install
[Lehko.apk](https://github.com/manoff4sv-lgtm/lehko-tv/releases/latest/download/Lehko.apk) (with the Downloader app,
or `scripts/install.sh TV_IP` from a computer), open Lehko → Home screen → Turn on, and allow debugging on the TV.
Lehko talks to the TV's own debugging port (127.0.0.1:5555) to make itself the home screen and, optionally, turn
off the stock launcher. Nothing is uninstalled.

**Go back:** Lehko → Home screen → Bring it back, or `scripts/revert.sh TV_IP` from a computer (works even if Lehko
is gone). Bring the original back before uninstalling Lehko. If Lehko's home screen crashes 3 times in 5 minutes,
the original comes back by itself.

**Privacy:** no accounts, analytics or ads; only Open-Meteo, YASNO and the outage-data-ua mirror are contacted, plus
Monobank, the NBU and Binance when the rates line is on.

**Build:** `./gradlew assembleRelease`. Plain Java, no libraries. MIT licensed. Inspired by
[Free My TV](https://github.com/simbasjungles/free-my-tv).
