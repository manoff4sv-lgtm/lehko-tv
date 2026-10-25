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
  захід сонця, повні курси, графік на завтра. OK там відкриває налаштування графіка відключень.
- **Улюблені додатки** з банерами, як на звичайному Android TV. Затисніть OK на додатку, щоб посунути, прибрати або
  видалити. Поки улюблених немає, у ряду стоїть плитка «Додати додатки»; потім усі додатки відкриває кругла кнопка
  «Усі додатки».
- **Фон під додаток.** Коли вибираєте улюблений додаток, фон плавно набуває темного відтінку його банера. Увечері,
  після заходу сонця, відтінок ще темніший.
- **Назва вибраного додатка** під рядом улюблених: на банерах її видно не завжди.
- **Погода** від Open-Meteo: зараз і «дощ з 17:00», а в подробицях мінімум і максимум, наступні 5 годин, схід і
  захід сонця. Працює без ключів і показує останній прогноз навіть без інтернету. Якщо оновити не вдається, а
  прогнозу вже понад 3 години, поруч зʼявляється «Не оновлювалося з 14:05»; заставка-годинник такий прогноз не показує.
  Місто вибирається в налаштуваннях. Якщо його не вибрано, погода показується для міста з графіка відключень (Київ,
  Дніпро чи Одеса), а без графіка для Києва, якщо телевізор у київському часовому поясі.
- **Курси:** долар і євро до гривні: продаж у Монобанку (або офіційний курс НБУ, якщо Монобанк не відповідає), у
  подробицях купівля і продаж. Вмикаються в налаштуваннях; останні значення видно й без інтернету.
- **Графік відключень світла**: зараз є чи нема і до котрої, коли відключать або ввімкнуть, кільце на сьогодні,
  смуга на завтра в подробицях. Якщо графік не вдається оновити понад 2 години (скажімо, під час відключення немає
  інтернету), у рядку зʼявляється «Не оновлювалося з 09:00» (з датою, якщо графік востаннє оновився не сьогодні).
  Так само в каміні. Поки графік не налаштований, телевізор у київському часовому поясі показує на місці рядка про
  світло «Графік світла · Вгору, потім OK: вкажіть адресу або групу»: натисніть «вгору», потім OK, і одразу
  відкриється вибір регіону. Якщо графік не потрібен, виберіть там «Вимкнено», і запрошення більше не зʼявиться.
  - Київ (місто): група за вашою адресою (з 2026 року в Києві 60 груп по будинках), дані YASNO, запасне джерело ДТЕК.
  - Київська область та Одеська область: дані ДТЕК.
  - Дніпропетровщина (ДнЕМ і ЦЕК): дані YASNO, група за адресою.
- **Попередження поверх будь-якого додатка.** За 15 хвилин до відключення у правому верхньому куті на 12 секунд
  зʼявляється невелика картка, наприклад «Через 15 хв відключення світла · з 18:00 до 21:00» (помаранчевим, якщо
  світла не буде, жовтим, якщо відключення можливе). Пульт вона не забирає, відео під нею йде далі. Якщо телевізор
  увімкнули вже ближче до відключення, картка покаже, скільки хвилин лишилося насправді. Коли графік на сьогодні чи
  завтра змінюється, так само один раз зʼявляється «Графік змінився: ...». Для цього, поки відкритий інший додаток,
  Lehko оновлює графік раз на пів години (у режимі очікування нічого не завантажує). Вимикається в налаштуваннях
  Lehko: «Попередження про відключення».
- **Таймер сну.** Кругла кнопка з місяцем: 15, 30, 60 або 90 хвилин. Поки таймер іде, назва кнопки каже, коли
  телевізор вимкнеться («Таймер сну · вимкнеться о 23:40»). За хвилину до кінця поверх будь-якого додатка
  зʼявляється картка: будь-яка кнопка залишає телевізор увімкненим і більше нічого не робить, тож відео не стане на
  паузу (гучність працює як завжди). Якщо нічого не натиснути, телевізор засне, як від кнопки живлення. Для цього
  потрібне налагодження USB; без нього на Android 9 і новіших Lehko просить Android заблокувати екран, а на Android 8
  картка попередить, що вимкнути телевізор не вийде. Якщо вимкнути телевізор раніше, таймер скасовується.
- **Кнопки пульта без стандартного лаунчера.** На Google TV кнопки ☆, YouTube і «усі додатки» на пульті обробляє
  стандартний лаунчер, тож коли його вимкнено, вони нічого не робили. Тепер, поки лаунчер вимкнений, їх перехоплює
  помічник Lehko: кнопка YouTube відкриває YouTube, кнопка «усі додатки» відкриває список додатків Lehko, а ☆
  відкриває додаток, вибраний у налаштуваннях Lehko в рядку «Кнопка ☆ на пульті» (він є, якщо на пульті є така
  кнопка), або, якщо нічого не вибрано, один з відомих IPTV-плеєрів, коли такий встановлений. Вибраний додаток ☆
  відкриває і з увімкненим лаунчером; «Як у телевізора» залишає кнопку системі. Інші кнопки помічник пропускає
  одразу і нічого з екрана не читає.
- **Після увімкнення телевізора** Lehko може одразу відкрити вхід HDMI чи ефірне ТБ замість головного екрана:
  налаштування Lehko, «Після увімкнення телевізора». Вхід відкривається, лише якщо телевізор прокинувся на головному
  екрані Lehko і ви ще нічого не натиснули. Якщо телевізор розбудила трансляція з телефона, приставка через HDMI-CEC
  чи кнопка додатка на пульті, лишається те, що ви попросили. Якщо телевізор був знеструмлений, вхід відкриється
  протягом приблизно пів хвилини після старту. Деякі телевізори після режиму очікування повертаються в останній
  додаток, а не на головний екран: тоді ця функція не спрацьовує.
- **Входи HDMI і ефірне ТБ** в один клік, налаштування телевізора, усі додатки, таймер сну: круглі кнопки внизу,
  назва зʼявляється під рядом улюблених.
- **Вимкнення зайвих додатків**, щоб звільнити памʼять. Нічого не видаляється, усе вмикається назад однією кнопкою.
- **Памʼять.** Кнопка «Памʼять» показує, скільки вільно. Натискання закриває додатки, які Android тримає у фоні
  (через режим налагодження; без нього звичайним способом Android, який працює до Android 13), і пише, скільки
  звільнилося. На Android 14 і новіших, якщо налагодження недоступне, кнопка пише, чому нічого не закрила.
  Автоочищення робить те саме автоматично, але лише коли сам Android каже, що памʼяті мало, і поки на головному
  екрані нічого не натискають (перед каміном, а без каміна приблизно через 5 хвилин без натискань), не частіше ніж
  раз на пів години. Тому спрацьовує воно нечасто; його можна вимкнути. Закриті додатки наступного разу
  відкриваються трохи довше. У налаштуваннях видно, скільки займає Lehko (разом з помічником) і скільки займав
  стандартний лаунчер.
- **Камін.** Якщо 5 хвилин нічого не натискати на головному екрані, запалюється камін: цегла, поліна, живий вогонь,
  тьмяний годинник і стан світла з графіка. Кнопки гучності й відтворення працюють як завжди, будь-яка інша повертає
  назад. Можна поставити 10 чи 15 хвилин або вимкнути. Тихе потріскування вмикається окремо і звучить лише в каміні
  у простої (заставка-камін завжди без звуку); поверх музики воно не звучить: замовкає, щойно вмикається музика чи
  відео. Екран камін не тримає: звичайна заставка і вимкнення телевізора працюють як завжди. Тому якщо таймер
  заставки телевізора не довший за час до каміна (5, 10 чи 15 хв), першою вмикається заставка телевізора, а камін
  пропускається. Тоді виберіть заставкою камін Lehko; рядок «Камін у простої» в налаштуваннях про це підкаже.
- **Заставка** на вибір: стандартна, годинник Lehko (темний, повільно переміщується) або камін Lehko (без звуку).
- **Оновлення.** Раз на день Lehko перевіряє на GitHub, чи вийшла нова версія, і один раз пише про неї на
  головному екрані. Щоб встановити: налаштування Lehko, «Оновлення», OK. Lehko завантажить файл, а телевізор
  попросить підтвердити встановлення. Першого разу Lehko потрібен дозвіл встановлювати додатки: з увімкненим
  налагодженням USB Lehko дає його собі сам (телевізор може спитати «Дозволити налагодження USB?»), інакше відкриється
  екран «Невідомі джерела» для Lehko: увімкніть там Lehko, поверніться і знову натисніть OK на «Оновлення». Після
  оновлення Lehko лишається головним екраном. Так оновлюються версії, новіші за 0.10.0-beta.2; її і старіші один раз
  оновіть вручну, як при встановленні.
- **Безпечно**: стандартний лаунчер лише вимикається, не видаляється. Кнопка «Повернути» поверне все як було.
  Якщо екран Lehko тричі впаде за 5 хвилин, телевізор сам повернеться до стандартного екрана. На Android 11 і новіших
  враховуються також зависання й збої нативного коду (наприклад, графічного драйвера).
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

Наступні версії можна встановлювати з самого Lehko (див. «Оновлення» вище).

### 3. Увімкніть

Відкрийте **Lehko** у списку додатків → **Головний екран** → **Увімкнути**. На екрані зʼявиться вікно
«Дозволити налагодження USB?»: позначте **«Завжди дозволяти»** і натисніть **OK**. Готово.

Разом з Lehko вмикається його помічник, «Помічник Lehko» у спеціальних можливостях телевізора. Він працює в
окремому маленькому процесі і потрібен для кнопок пульта, попереджень про відключення, таймера сну і входу після
увімкнення телевізора.

Галочка «Також вимкнути стандартний лаунчер» звільняє найбільше памʼяті, але за замовчуванням вимкнена. Спершу
спробуйте без неї. Якщо кнопка Home все одно відкриває стандартний екран, вимкніть лаунчер окремо: Lehko →
Головний екран → **Вимкнути стандартний лаунчер** → **Вимкнути**. Там само його можна знову ввімкнути (**Увімкнути
стандартний лаунчер знову**), а Lehko лишиться головним екраном. Якщо щось не ввімкнеться, Lehko напише, що саме, і
пункт лишиться в списку, щоб спробувати ще раз. Повертати для цього стандартний екран не треба: це ще й увімкнуло б
назад усі зайві додатки і скинуло заставку. **Поки лаунчер вимкнений, завжди повертайте стандартний екран перед
видаленням Lehko.**

> Не вимикайте «Налагодження USB» після встановлення: через нього Lehko перевіряє себе після оновлень телевізора.
> Дозволяйте налагодження лише тоді, коли самі щойно натиснули кнопку в Lehko.

**Якщо телевізор забув дозвіл** (Lehko про це напише): Lehko → Головний екран → **Дозволити налагодження знову**,
і у вікні позначте «Завжди дозволяти».

**Якщо порт закривається після перезавантаження.** Деякі телевізори відкривають порт налагодження лише командою `adb
tcpip 5555` з компʼютера і закривають його після кожного перезавантаження. Тоді виконуйте її після кожного
перезавантаження телевізора або ввімкніть «Налагодження по мережі», якщо воно є. Поки порт закритий, `adb connect
IP:5555` теж не працює, тож підключіться до телевізора інакше: USB-кабелем (якщо телевізор має USB-порт для компʼютера;
на більшості телевізорів є лише порти для флешок) або, на Android 11 і новіших, через «Бездротове налагодження»
(`adb pair` з кодом сполучення, `adb connect` на адресу з того самого екрана, потім `adb -s IP:ПОРТ tcpip 5555`). Із
закритим портом Lehko лишається головним екраном, але не може перевірити себе після оновлень телевізора і не зможе сам
повернути стандартний екран, якщо екран Lehko почне падати. Налаштування Lehko, екран «Зайві додатки» і, на Android 14 і
новіших, кнопка «Памʼять» скажуть, що порт закритий, а після перезавантаження про це зʼявиться повідомлення на головному
екрані.

## Якщо щось не так

- **☆ відкриває додаток двічі.** Найімовірніше, на телевізорі лишився увімкненим помічник кнопок від іншого
  лаунчера, наприклад `ua.lehkyi.launcher/.RemoteKeys`: тоді на кожне натискання відповідають обидва. Перевірте з
  компʼютера:

  ```sh
  adb shell settings get secure enabled_accessibility_services
  ```

  Запис Lehko виглядає як `ua.lehko.tv/.TvHelper`. Інший помічник вимкніть у спеціальних можливостях телевізора або
  засобом відкату того лаунчера. Чужих служб Lehko не чіпає.
- **Кнопки пульта, попередження і таймер сну перестали працювати.** Перевірте, чи увімкнено «Помічник Lehko» у
  спеціальних можливостях телевізора. Якщо ви вимкнули його там, Lehko сам його не вмикає: увімкніть там само. Після
  примусової зупинки Lehko Android вимикає помічника сам; тоді Lehko повертає його (через налагодження USB), щойно ви
  повернетеся на головний екран.

## Як повернути все назад

- **На телевізорі:** Lehko → Головний екран → **Повернути стандартний екран** → **Повернути**. Усе, що вимикав
  Lehko, знову ввімкнеться: і лаунчер, і додатки з екрана «Зайві додатки». Помічник Lehko вимкнеться, інші служби
  спеціальних можливостей лишаться як були.
- **Стандартний екран уже працює, а зайві додатки ще вимкнені:** Lehko → **Увімкнути додатки назад** (цей рядок
  зʼявляється в налаштуваннях сам, поки є додатки, які Lehko вимкнув і не ввімкнув назад).
- **З компʼютера**, навіть якщо Lehko вже видалено: `scripts/revert.sh 192.168.1.50` (або `revert.bat`). Додайте
  `--uninstall`, щоб заразом видалити Lehko. Скрипт так само прибирає помічника Lehko. Якщо Lehko лишився, він
  помітить такий відкат, щойно наступного разу звернеться до телевізора, і сам головним екраном знову не стане: ні
  після перезавантаження, ні після оновлення.

**Перед видаленням Lehko спершу натисніть «Повернути»** (або «Увімкнути додатки назад», якщо стандартний екран уже
працює), інакше телевізор може залишитися без головного екрана, а вимкнені додатки так і лишаться вимкненими.

## Що саме змінюється

- Lehko стає головним екраном (`cmd package set-home-activity`).
- Якщо галочка увімкнена, вимикаються (`pm disable-user`, не видаляються): стандартний лаунчер; на Google TV ще
  екран відновлення з майстра налаштування (інакше він займає місце лаунчера); на старому Android TV ще сервіс рекомендацій.
- Вмикається помічник Lehko, служба спеціальних можливостей «Помічник Lehko» (`ua.lehko.tv/.TvHelper` у
  `enabled_accessibility_services` поруч з уже увімкненими службами, і `accessibility_enabled` 1). «Повернути» і
  скрипт відкату прибирають лише його запис; якщо інших служб не лишилося, `accessibility_enabled` стає таким, як був.
- Додатки, які зберегли вашу мережу Wi-Fi, ніколи не вимикаються: Android тоді забув би мережу. Системні служби,
  магазин, клавіатура, пульт і входи телевізора в захищеному списку. Крім того, на екрані «Зайві додатки» не
  пропонуються головний екран, вибрана клавіатура, служби доступності, заставка, ефірне ТБ і плеєр HDMI.
- Lehko ставить свою заставку лише тоді, коли ви самі вибираєте її в налаштуваннях Lehko. Пункт «Стандартна» там
  повертає ту, що була раніше, а якщо заставку Lehko вибрали в налаштуваннях телевізора, то стандартну заставку Android.
  «Повернути» і скрипт відкату повертають попередню заставку, лише якщо заставку Lehko поставив сам Lehko і вона досі
  стоїть. Заставку, вибрану в налаштуваннях телевізора (навіть заставку Lehko), вони не чіпають.
- На Android 11+ вимикається тайм-аут дозволу налагодження (як пункт «Disable adb authorization timeout» у Параметрах
  розробника), інакше через 7 днів телевізор забуває дозвіл і страховка Lehko не спрацює. «Повернути» і скрипт
  відкату відновлюють як було.
- Таймер сну присипляє телевізор командою `input keyevent KEYCODE_SLEEP`.
- Коли ви натискаєте «Оновлення», а Lehko ще не може встановлювати додатки, він дозволяє це собі командою
  `appops set ua.lehko.tv REQUEST_INSTALL_PACKAGES allow` (те саме, що «Невідомі джерела» для Lehko). Кожне
  встановлення однаково підтверджуєте ви на екрані телевізора. «Повернути» цей дозвіл не забирає.
- Список змін Lehko також пише в `/data/local/tmp/lehko_state` на телевізорі: його читають скрипт відкату з
  компʼютера і сам Lehko після перевстановлення.
- Якщо екран Lehko тричі впаде за 5 хвилин, вмикається безпечний режим: Lehko показує лише просте повідомлення з
  кнопками «Спробувати ще раз», «Налаштування ТВ» і «Налаштування Lehko» і одразу повертає стандартний екран. Якщо
  повернути не вдалося, повідомлення підкаже, що робити (наприклад, що порт налагодження закритий і як його відкрити).

## Приватність

Без акаунтів, аналітики і реклами. Lehko звертається лише до:
- [Open-Meteo](https://open-meteo.com) за погодою (координати вибраного міста);
- [YASNO](https://yasno.ua) і [outage-data-ua](https://github.com/Baskerville42/outage-data-ua) (дзеркало графіків ДТЕК на GitHub) за графіками відключень, якщо ви їх увімкнули;
- [Монобанк](https://api.monobank.ua/docs/) і [НБУ](https://bank.gov.ua/ua/open-data/api-dev) за курсами валют, якщо вони увімкнені;
- [GitHub](https://github.com/manoff4sv-lgtm/lehko-tv/releases) раз на день, щоб дізнатися, чи є нова версія Lehko, і за самим оновленням, коли ви натиснете «Оновлення».

Ваша адреса для пошуку групи надсилається лише в YASNO і зберігається тільки на телевізорі.

## Збірка

```sh
./gradlew assembleRelease     # APK: app/build/outputs/apk/release/
./gradlew test
```

Без Android Studio: `tools/quick-build.sh` і `tools/quick-test.sh` (aapt2 + javac + dx). Тести ADB і перемикання
головного екрана працюють проти `tools/fake_adbd.py --tv`, симулятора Google TV (Wi-Fi мережі, роль HOME, пакети,
служби спеціальних можливостей). Рядки інтерфейсу: `tools/strings/strings.py` генерує `values`, `values-uk`,
`values-ru`.

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
1 GB Xiaomi TV A, works on any Android TV 8+). On TVs with 1 GB of memory the stock Google TV home screen is slow and
shows ads. Lehko replaces it with a simple, fast screen: a clock, the weather, your building's power outage schedule,
exchange rates, favorite apps and HDMI inputs. No ads and no heavy effects: Lehko itself takes a few tens of megabytes.

### What it does

- **A quiet screen.** The clock sits in a 24-hour ring, and the ring is today's power schedule: orange arcs mean no
  power, a dot marks "now". Beside it, three short lines: power (on or off, and until when), the weather, the rates.
  Up from the favorites opens the details: the weather by the hour, sunrise and sunset, full rates, tomorrow's
  schedule. OK there opens the outage schedule's settings.
- **Favorite apps** with banners, as on a regular Android TV. Hold OK on an app to move, remove or uninstall it.
  While there are no favorites, the row has an "Add apps" tile; after that, the round "All apps" button opens every app.
- **A background for the app.** When a favorite has focus, the background fades to a dark tint of its banner; after
  sunset the tint is darker still.
- **The focused app's name** under the favorites row: banners don't always show it.
- **Weather** from Open-Meteo: now and "rain from 17:00", and in the details the low and high, the next 5 hours,
  sunrise and sunset. It needs no keys and shows the last forecast offline. When updating fails and the forecast is
  over 3 hours old, "No update since 14:05" appears beside it; the clock screensaver leaves such a forecast out.
  Choose the city in the settings. With none chosen, the weather follows the outage schedule's region (Kyiv, Dnipro
  or Odesa), or, with no schedule, Kyiv on a TV in the Kyiv time zone.
- **Rates:** the dollar and the euro in hryvnias: Monobank's sell rate (or the NBU's official rate when Monobank
  doesn't answer), with buy and sell in the details. Turned on in the settings; the last values show offline.
- **Power outage schedule**: power now or not and until when, when it goes off or comes back, today's ring,
  tomorrow's bar in the details. When the schedule can't be updated for over 2 hours (say, there is no internet during
  an outage), the line says "No update since 09:00" (with the date if the last update wasn't today). The fireplace
  says it too. Until the schedule is set up, a TV in the Kyiv time zone shows "Power schedule · Up, then OK: enter
  your address or group" in the power line's place: press Up, then OK, and the region choice opens at once. If you
  don't need the schedule, pick "Off" there and the invitation won't come back.
  - Kyiv city: your group by address (since 2026 Kyiv has 60 per-building groups), YASNO data, DTEK as the fallback.
  - Kyiv region and Odesa region: DTEK data.
  - Dnipro region (DnEM and CEK): YASNO data, group by address.
- **Outage warning over any app.** 15 minutes before an outage a small card shows at the top right for 12 seconds,
  for example "Power outage in 15 min · 18:00 to 21:00" (orange for no power, yellow for a possible outage). It
  doesn't take the remote, and the video under it plays on. If the TV is turned on closer to the outage, the card
  gives the minutes really left. When today's or tomorrow's schedule changes, "The schedule changed: ..." shows once
  the same way. For this, Lehko updates the schedule every half hour while another app is in front (nothing in
  standby). Turn it off in Lehko settings: "Outage warning".
- **Sleep timer.** A round moon button: 15, 30, 60 or 90 minutes. While it runs, the button's name says when the TV
  turns off ("Sleep timer · off at 23:40"). A minute before the end a card comes up over any app: any button keeps
  the TV on and does nothing else, so the video doesn't pause (volume works as usual). If nothing is pressed, the TV
  goes to sleep as with the power button. That needs USB debugging; without it, on Android 9 and later Lehko asks
  Android to lock the screen, and on Android 8 the card says the TV can't be turned off. Turning the TV off earlier
  cancels the timer.
- **Remote buttons without the stock launcher.** On Google TV the stock launcher answers the remote's ☆, YouTube and
  "all apps" buttons, so with the launcher off they did nothing. Now, while the launcher is off, Lehko's helper takes
  them: the YouTube button opens YouTube, the "all apps" button opens Lehko's app list, and ☆ opens the app chosen in
  Lehko settings under "Star button on the remote" (shown when the remote has that button) or, with nothing chosen,
  one of a few well-known IPTV players when one is installed. A chosen app opens with the launcher on too; "As the TV
  does" leaves the button to the TV. The helper passes every other button straight on and reads nothing on the screen.
- **When the TV turns on**, Lehko can open an HDMI input or Live TV instead of the home screen: Lehko settings, "When
  the TV turns on". The input opens only when the TV wakes up to Lehko's home screen and you haven't pressed anything
  yet. If casting from a phone, a console over HDMI-CEC or an app's button on the remote woke the TV, what you asked
  for stays. After the TV was off at the wall, the input opens within about half a minute of the start. Some TVs come
  back from standby to the last app rather than the home screen; on those this choice does nothing.
- **HDMI inputs and Live TV** in one click, the TV's settings, all apps and the sleep timer: round buttons at the
  bottom, with the name under the favorites row.
- **Turning off unneeded apps** to free memory. Nothing is uninstalled, and one button turns everything back on.
- **Memory.** The "Memory" button shows how much is free. Pressing it closes the apps Android keeps in the background
  (through debugging; without it, Android's own way, which works up to Android 13) and says how much was freed. On
  Android 14 and later, when debugging can't be reached, the button says why nothing was closed. Auto clean does the
  same by itself, but only when Android itself says memory is low and while nothing is pressed on the home screen
  (just before the fireplace, or after about 5 idle minutes without one), at most every half hour. So it runs rarely;
  it can be turned off. Closed apps take a little longer to open the next time. The settings show how much Lehko
  (with its helper) takes and how much the stock launcher took.
- **Fireplace.** After 5 idle minutes on the home screen a fireplace lights up: bricks, logs, a live fire, a dim
  clock and the power status from the schedule. Volume and playback buttons work as usual; any other button goes back.
  You can set 10 or 15 minutes or turn it off. A quiet crackle is turned on separately and plays only by the idle
  fireplace (the fireplace screensaver is always silent); it never plays over music and stops as soon as music or a
  video starts. The fireplace doesn't keep the screen on: the TV's screensaver and sleep work as usual. So if the
  TV's screensaver timer is no longer than the time before the fireplace (5, 10 or 15 min), the TV's screensaver
  comes first and the fireplace is skipped. Then pick the Lehko fireplace as the screensaver; the "Fireplace when
  idle" row in the settings says so.
- **Screensaver** of your choice: the TV's own, the Lehko clock (dark, moving slowly) or the Lehko fireplace (silent).
- **Updates.** Once a day Lehko asks GitHub whether a new version is out and says so once on the home screen. To
  install it: Lehko settings, "Update", OK. Lehko downloads the file, and the TV asks you to confirm the install. The
  first time, Lehko needs permission to install apps: with USB debugging on, Lehko gives it to itself (the TV may ask
  "Allow USB debugging?"); otherwise the TV's "Unknown sources" screen for Lehko opens: switch Lehko on there, come
  back and press OK on "Update" again. After the update Lehko stays the home screen. Versions newer than 0.10.0-beta.2
  update this way; update that one or an older one by hand once, as when installing.
- **Safe.** The stock launcher is only turned off, never uninstalled. "Bring back" puts everything back as it was.
  If Lehko's screen crashes 3 times in 5 minutes, the TV goes back to the stock home screen by itself. On Android 11
  and later hangs and native crashes (of the graphics driver, say) count too.
- **Accessibility.** Text passes WCAG AA contrast even on the tinted background, focus always has a white outline,
  the outage schedule reads without telling red from green (power on: thin line, off: thick arc, possible: dashed).
  TalkBack reads the weather, the schedule and notices. With animations turned off in the TV's settings the
  fireplace stands still.
- Ukrainian, English and Russian (as the TV's system language).

### Install

#### 1. Turn on debugging on the TV

Google TV and Android TV let an app change the home screen only through debugging. Lehko uses it right on the TV; no
computer needed.

1. **Settings → System → About** → press **"Android TV OS build"** 7 times.
2. **Settings → System → Developer options** → turn on **"USB debugging"**.

#### 2. Install Lehko

**Without a computer.** Install **Downloader** (by AFTVnews) from Google Play, open it and enter the address:

```
https://github.com/manoff4sv-lgtm/lehko-tv/releases/latest/download/Lehko.apk
```

Allow Downloader to install apps when the TV asks.

**From a computer** on the same Wi-Fi network (needs [adb](https://developer.android.com/tools/releases/platform-tools)):

```sh
scripts/install.sh 192.168.1.50      # macOS / Linux
scripts\install.bat 192.168.1.50     # Windows
```

The TV's IP address is in **Settings → Network & Internet → your network**.

Later versions can be installed from Lehko itself (see "Updates" above).

#### 3. Turn it on

Open **Lehko** in the app list → **Home screen** → **Turn on**. The TV shows "Allow USB debugging?": tick **"Always
allow"** and press **OK**. Done.

Lehko's helper comes on with it: "Lehko helper" in the TV's accessibility settings. It runs in a small process of
its own and is needed for the remote's buttons, the outage warning, the sleep timer and the input to open when the
TV turns on.

"Also turn off the stock launcher" frees the most memory but is off by default. Try without it first. If Home still
opens the stock screen, turn the launcher off separately: Lehko → Home screen → **Turn off the stock launcher** →
**Turn off**. The same list turns it back on (**Turn the stock launcher back on**) while Lehko stays the home screen.
If something doesn't come back on, Lehko says what and keeps the item there to try again. No need to switch back for
that, which would also turn the unneeded apps back on and reset the screensaver. **While the launcher is off, always
bring back the TV's home screen before uninstalling Lehko.**

> Keep USB debugging on after installing: Lehko checks itself through it after TV updates. Allow debugging only when
> you have just pressed a button in Lehko yourself.

**If the TV forgets the permission** (Lehko says so): Lehko → Home screen → **Allow debugging again**, and tick
"Always allow".

**If the port closes after a restart.** Some TVs open the debugging port only after `adb tcpip 5555` from a computer
and close it on every restart. Then run it again after each restart, or turn on Network debugging if the TV has it.
While the port is closed, `adb connect TV_IP:5555` fails too, so reach the TV another way: over a USB cable (only if
the TV has a USB port for a computer; most TV sets have only ports for flash drives) or, on Android 11 and later,
through Wireless debugging (`adb pair` with the pairing code, `adb connect` to the address on the same screen, then
`adb -s IP:PORT tcpip 5555`). With the port closed Lehko stays the home screen, but it can't check itself after TV
updates, and it can't switch back by itself if Lehko's screen starts crashing. Lehko's settings, Unneeded apps and, on
Android 14 and later, the Memory button say that the port is closed, and after a restart a notice on the home screen
says so too.

### If something is wrong

- **☆ opens an app twice.** Most likely a remote buttons helper from another launcher is still on, for example
  `ua.lehkyi.launcher/.RemoteKeys`: then both answer every press. Check from a computer:

  ```sh
  adb shell settings get secure enabled_accessibility_services
  ```

  Lehko's entry reads `ua.lehko.tv/.TvHelper`. Turn the other helper off in the TV's accessibility settings, or with
  that launcher's own revert. Lehko never touches other services.
- **The remote's buttons, the warning and the sleep timer stopped working.** Check that "Lehko helper" is on in the
  TV's accessibility settings. If you turned it off there, Lehko doesn't turn it on by itself: turn it on there again.
  A force stop of Lehko makes Android turn the helper off; Lehko then puts it back (through USB debugging) as soon as
  you return to the home screen.

### How to put everything back

- **On the TV:** Lehko → Home screen → **Bring back the TV's home screen** → **Bring it back**. Every app Lehko turned
  off comes back on, the stock launcher and the apps from Unneeded apps alike. Lehko's helper is turned off; other
  accessibility services stay as they are.
- **The TV's home screen is in use already, but unneeded apps are still off:** Lehko → **Turn apps back on** (the row
  shows in the settings by itself while some app Lehko turned off hasn't been turned back on by Lehko).
- **From a computer**, even if Lehko is gone already: `scripts/revert.sh 192.168.1.50` (or `revert.bat`). Add
  `--uninstall` to uninstall Lehko as well. The script takes Lehko's helper out too. A Lehko still installed notices
  such a revert the next time it talks to the TV and won't take the home screen back by itself, either after a
  restart or after an update.

**Before uninstalling Lehko, press "Bring back" first** (or "Turn apps back on" if the TV's home screen is in use
already), or the TV may be left without a home screen and the apps turned off stay off.

### What exactly changes

- Lehko becomes the home screen (`cmd package set-home-activity`).
- With the box ticked, these are turned off (`pm disable-user`, not uninstalled): the stock launcher; on Google TV
  also the setup wizard's recovery screen (it takes the launcher's place otherwise); on older Android TV also the
  recommendations service.
- Lehko's helper comes on, the accessibility service "Lehko helper" (`ua.lehko.tv/.TvHelper` in
  `enabled_accessibility_services`, next to the services already on, and `accessibility_enabled` 1). "Bring back"
  and the revert script take out only its entry; when no other service is left, `accessibility_enabled` goes back to
  what it was.
- Apps that saved your Wi-Fi network are never turned off: Android would forget the network. System services, the
  store, the keyboard, the remote and the TV's inputs are on a protected list. Besides, Unneeded apps never offers the
  home screen, the keyboard in use, accessibility services, the screensaver, Live TV or the HDMI player.
- Lehko sets its own screensaver only when you pick it in Lehko's settings. "The TV's own" there brings back the one
  you had before, or Android's default when Lehko's was picked in the TV's own settings. "Bring back" and the revert
  script put the earlier screensaver back only if Lehko set its own and it is still set. A screensaver picked in the
  TV's settings (even Lehko's) stays.
- On Android 11 and later the debugging permission timeout is turned off (like "Disable adb authorization timeout" in
  Developer options); otherwise the TV forgets the permission after 7 days and Lehko's safety net won't work. "Bring
  back" and the revert script restore it.
- The sleep timer puts the TV to sleep with `input keyevent KEYCODE_SLEEP`.
- When you press "Update" and Lehko can't install apps yet, it allows itself with
  `appops set ua.lehko.tv REQUEST_INSTALL_PACKAGES allow` (the same as "Unknown sources" for Lehko). You still confirm
  every install on the TV's screen. "Bring back" leaves this permission as it is.
- Lehko also writes its changes to `/data/local/tmp/lehko_state` on the TV: the revert script on a computer and Lehko
  itself after a reinstall read it.
- If Lehko's screen crashes 3 times in 5 minutes, safe mode comes on: Lehko shows only a plain message with "Try
  again", "TV settings" and "Lehko settings" buttons and brings back the stock home screen at once. If that fails, the
  message says what to do (for example, that the debugging port is closed and how to open it).

### Privacy

No accounts, analytics or ads. Lehko talks only to:
- [Open-Meteo](https://open-meteo.com) for the weather (the chosen city's coordinates);
- [YASNO](https://yasno.ua) and [outage-data-ua](https://github.com/Baskerville42/outage-data-ua) (a mirror of DTEK's schedules on GitHub) for outage schedules, if you turned them on;
- [Monobank](https://api.monobank.ua/docs/) and the [NBU](https://bank.gov.ua/ua/open-data/api-dev) for exchange rates, if they are on;
- [GitHub](https://github.com/manoff4sv-lgtm/lehko-tv/releases) once a day to see whether a new Lehko is out, and for the update itself when you press "Update".

Your address for the group lookup goes only to YASNO and is stored only on the TV.

### Build

```sh
./gradlew assembleRelease     # APK: app/build/outputs/apk/release/
./gradlew test
```

Without Android Studio: `tools/quick-build.sh` and `tools/quick-test.sh` (aapt2 + javac + dx). The ADB and
home-switching tests run against `tools/fake_adbd.py --tv`, a simulated Google TV (Wi-Fi networks, the HOME role,
packages, accessibility services). UI strings: `tools/strings/strings.py` generates `values`, `values-uk`, `values-ru`.

Code: Java without libraries (no AndroidX), so the app weighs about 200 KB and takes little memory.

### Credits

- [Free My TV](https://github.com/simbasjungles/free-my-tv): the idea and the approach with a built-in ADB client.
- [Open-Meteo](https://open-meteo.com), [YASNO](https://yasno.ua), [DTEK](https://www.dtek-kem.com.ua/ua/shutdowns),
  [outage-data-ua](https://github.com/Baskerville42/outage-data-ua), Monobank, the NBU.
- The fire in the fireplace: the classic "Doom fire" algorithm (Fabien Sanglard described it in "How DOOM fire was
  done").

[MIT](LICENSE) licensed.
