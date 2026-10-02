# Fieldwatch

I built Fieldwatch as a personal tool to look at what Wi-Fi access points and Bluetooth LE ads my phone was able to pick up, so that I could better understand what devices were being used around me. It’s passive, it only listens, there’s no dongle, no account, and no backend server. I wanted something that would work offline in the field.

My goals were to have a modern interface that was easy to use, flexible in how information was displayed so I could customize a view based on what I was trying to do, a filtering engine so I do not have to look at everything, an extensible signature library so I can identify as many radio sources as possible and add new ones on the fly, as well as create reports of what was seen.

I have been using it and iterating on it for a while now, and it has been useful enough that I thought I would share it.

This is a hobby — something I do for fun in my spare time. There is no Fieldwatch backend. There are no ads. Everything lives on the phone. Source and sideload files are in this repository (`dist/` for the APK, instruction card, and manual).

It was Spectre through 1.2.14. I later learned that name was already in use by another app, so I renamed this one Fieldwatch to keep the two from being mixed up. Same field tool, new application id (`app.fieldwatch`), MIT License. Installing Fieldwatch does not replace Spectre on a phone; it is a separate app.

If you spot an error, something stupid, or have a feature idea — in the app or the documentation — please [open an issue on this repository](https://github.com/OffGridPete/Fieldwatch/issues). This is how we make it better. I hope you find it as useful as I have. I look forward to hearing how it goes.

**Just want to install it?** Download [Fieldwatch.apk](https://github.com/OffGridPete/Fieldwatch/raw/main/dist/Fieldwatch.apk). Instruction card and manual: [instruction.txt](https://github.com/OffGridPete/Fieldwatch/raw/main/dist/instruction.txt), [Fieldwatch_User_Manual.pdf](https://github.com/OffGridPete/Fieldwatch/raw/main/dist/Fieldwatch_User_Manual.pdf). [What’s new](CHANGELOG.md) is the changelog for each build. Leave the APK named `Fieldwatch.apk`. GitHub may say the file is too big to preview — that is their viewer; use Download.

## 界面语言

支持英语和简体中文，默认跟随手机系统语言；其他系统语言使用英语。进入“设置 → 语言”可选择“跟随系统”、English 或简体中文，立即刷新界面并保存选择，重启后仍然有效。Android 13 及以上的系统应用语言设置与应用内选择保持同步。设置导入/导出包含语言选择。设备详情中的内置特征备注、额外关注说明、解析字段与状态，以及蓝牙外观、设备类别和标准服务名称随界面语言显示。监测简报（文本/PDF）、观测对比、AI 导出、设备详情分享及地图文件中的可读说明也按当前语言导出。设备广播名称、MAC/UUID 等协议标识、地名和自定义内容保留原文；特征库存储、CSV/JSONL/WiGLE 等机器可读格式的字段和 MIT 许可证保留原始内容。

## 设备识别与广播详情

JSONL 监测导出及轮转日志包含手机系统公开的完整原始广播、制造商数据、服务 UUID、服务数据、Wi-Fi 厂商信息元素和无线参数。已保存观测也保留这些样本；旧观测文件仍可读取，但不能补回保存时未记录的广播数据。CSV 字段保持兼容。

Wi-Fi 的 WPS 信息可解析厂商、型号、型号编号、设备名称和广播序列号，并在实时类型行、设备详情、分享文本及监测简报中显示型号。WPA、WMM、WPS 信息元素按协议名称显示；它们的 OUI 不代表接入点的产品厂商。型号和序列号都是设备广播的声明，不保证是真实或唯一的硬件身份。

内置识别补充国内常见路由器、家居设备、温湿度计、秤、音频设备和电动车的广播线索。特征库升级自动补充新条目并保留已有自定义内容。运营商网络名称和未公开的厂商载荷不足以确认具体设备型号。

新增 MiBeacon 与小米秤的广播解析。明文测量字段按协议显示；加密测量仅显示产品标识和协议状态，不将密文解析为温湿度。秤的空载或不稳定广播明确标注状态。设备详情及报告中的新增说明支持英语和简体中文，广播名称与标识保留原文。解析保持被动扫描，不主动连接附近设备。

## Safety & disclaimer

This is a hobby project, provided as-is under the MIT License. A few things to know before you do:

- Use at your own risk. Using Fieldwatch is your responsibility. To the maximum extent permitted by law, Off Grid Pete LLC is not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.
- There is no guarantee that trackers, cameras, tags, access points, or any other device will be found, named, or reported. Radios that are off, cellular-only, asleep, randomized, quiet, or outside what this handset’s OS exposes will not appear. Each phone has its own radios, firmware, scan quotas, and OEM battery policies. Software cannot address those limits.
- Pattern matches, GPS co-travel (“Moving with you” / “possible tail”), Debrief language, and AI Export output are hypotheses — not identity, not a legal finding, and not a complete RF capture. You are solely responsible for how you use this app and this document, and for complying with local law. By using the software or this manual you accept these terms and the MIT License.
- Location data, if tagging is on, is this phone at hear-time — not the other radio. There is no Fieldwatch server. Stamps stay on the handset until you share them. Logs keep full coordinates even when Privacy mode masks the screen and sit reports. Debrief, Share log, AI Export (sit or one radio), and radio-detail Share as text can take that path off the phone. Online place names use the system geocoder (often the OEM / Google network), not a Fieldwatch cloud. How you store, share, or publish those files is your responsibility.

## Put it on a phone

On a phone, the install files are in [`dist/`](https://github.com/OffGridPete/Fieldwatch/tree/main/dist), not the root of the repo:

- [Fieldwatch.apk](https://github.com/OffGridPete/Fieldwatch/raw/main/dist/Fieldwatch.apk)
- [instruction.txt](https://github.com/OffGridPete/Fieldwatch/raw/main/dist/instruction.txt)
- [Fieldwatch_User_Manual.pdf](https://github.com/OffGridPete/Fieldwatch/raw/main/dist/Fieldwatch_User_Manual.pdf)

Do not rename the APK. Open `Fieldwatch.apk` from Files (or My Files). Allow install from that app if Android asks.

| File | What it is |
|---|---|
| `dist/Fieldwatch.apk` | Sideload APK |
| `dist/fieldwatch-signatures.json` | Stock catalog for 1.1.11 GitHub update (catalog 77) |
| `dist/fieldwatch-signatures-v2.json` | Stock catalog for 1.1.12+ GitHub update |
| `dist/instruction.txt` | Permissions, first launch |
| `dist/Fieldwatch_User_Manual.pdf` | User manual |
| [`CHANGELOG.md`](CHANGELOG.md) | What’s new in each build |
| `LICENSE` | MIT License |
| `NOTICE` | Third-party attribution |

Android 10+. Allow install from the app you used to open the APK. Play Protect may warn that it is not from Play — expected. Full steps are in `instruction.txt`.

```bash
adb install -r dist/Fieldwatch.apk
```

### Upgrading from 1.0.4 or earlier — Fieldwatch now has a real publisher certificate (one-time reinstall)

This release is a little more professional about how the APK is signed. Android attaches a certificate to every app so the phone can tell “this update is from the same publisher as the app I already have.” Through 1.0.4, Fieldwatch used the generic Android developer certificate that the build tools ship with. That is normal while you are iterating, but people who scan a sideload APK (and some scanners) flag it: a public build should not look like a debug leftover. 1.0.5 is signed with an Off Grid Pete LLC certificate instead. Same hobby app; the file now has a publisher name scanners can check. The fingerprint is in `instruction.txt` if you want to compare.

The catch is one-time. The phone treats a new certificate as a different publisher, so it will not install 1.0.5 on top of 1.0.4 or earlier. You uninstall the old Fieldwatch, then install this APK. After that, later versions use the same certificate, so ordinary updates work again. You will not have to uninstall for 1.1.17.

Uninstall wipes what is on the phone. If you added signatures, changed Settings, named radios, or saved filter presets, do this first: Settings → **Export signatures** and **Export settings**. Share or save those two files somewhere you can get them after. They are not the log and not GPS. Then uninstall (long-press the Fieldwatch icon, or `adb uninstall app.fieldwatch`), install 1.0.5, open it, tap through the disclaimer, and use **Import signatures** and **Import settings**. If you never customized, skip the export and just uninstall, then install.

## What Fieldwatch is not

- Not Wi-Fi clients, probe-only stations, or 802.11 monitor mode
- Not Bluetooth Classic inquiry (HC-05 / HC-06 will not appear)
- Not cellular
- Not direction finding

## Copyright and license

Copyright (c) 2026 Off Grid Pete LLC.

Fieldwatch source is licensed under the [MIT License](LICENSE). AndroidX, Kotlin, and related libraries remain Apache-2.0. IEEE and Bluetooth SIG assigned-number tables in `radiodb.bin` are subject to those organizations’ terms. See [NOTICE](NOTICE).

### 特征库 90：外出样本识别修正

- MERCURY 使用 Wi-Fi 名称前缀匹配；升级时将原始生成候选（名称 MERCURY、自动候选备注）收窄到 MERCURY* 名称规则，保留条目 ID、名称、开关及备注。不改其他自建特征。名称候选不再自动合并共享硬件标识作为“任一命中”条件。
- ROMO-* BLE 单独归为 DJI ROMO 扫地机器人，并抑制通用 DJI 无人机类别；不推断零售型号或工作状态。
- 增加摩拜车锁：名称 mobike、厂商 04B3 或专用服务 UUID。仅被动识别，不连接或推断锁状态。
- MiBeacon 增加已核验产品 ID 型号映射，覆盖 S400、MJWSD05MMC、MJWSD06MMC、LYWSD02MMC、部分门锁及传感器；加密测量仍不解码。详情及报告共用解码和英中翻译。
- 来源：[水星默认无线名称](https://service.mercurycom.com.cn/article-1489.html)、[DJI ROMO](https://www.dji.com/media-center/insights/cleaning-tips-for-pet-owners-robot-vacuum-guide)、[摩拜 BLE 车锁](https://www.nordicsemi.com/Nordic-news/2017/06/Mobike-smart-lock)、[MiBeacon 型号表](https://github.com/Bluetooth-Devices/xiaomi-ble/blob/main/src/xiaomi_ble/devices.py)。
### 特征库 91：未知设备的第二轮识别

新增 21 个系列，并按 2026-10-02 下载的 [IEEE MA-L 登记表](https://standards-oui.ieee.org/oui/oui.csv) 补充真实日志中出现的 H3C、小米、中兴地址前缀，以及斐讯、奇虎、海尔登记前缀。路由器地址规则仅用于 Wi-Fi；海尔蓝牙地址规则仅用于稳定公开地址。通用芯片地址、FEE7、HID 或串口服务不单独作为产品品牌证据。

| 识别范围 | 依据与识别边界 |
| --- | --- |
| BJP010?B??? 停放区信标 | [CN114373293B 专利](https://patents.google.com/patent/CN114373293B/zh)明确列出 BJP0102B001 信标命名示例；归为信标，不归为车辆，不解析位置。 |
| QJLF30 骑安／青桔相关设备 | 名称和广州骑安的公开 IEEE 前缀 24:F1:50 必须同时匹配；[交通部门资料](https://jtw.sh.gov.cn/cmsres/8d/8d519f60c2744bc3b89917f33b57cbc6/9587fff6168f4efffa79b390c2d0a5c0.pdf)确认广州骑安运营青桔。仅识别运营商系列，车锁与停车设施尚未区分，保留在“其他”类别。 |
| NIU Link、CFMOTO、IngeekDK、ZeekrVehicle | 根据具体广播名称与[小牛](https://global.niu.com/product/NQiX-Features)、[春风动力](https://www.cfmoto.com/global/media-center/news/news/cfmoto-introduces-unbeatable-sports-performance-with-the-cfmoto-.html)、[银基数字钥匙](https://www.ingeek.com/solution/boutique)、[极氪蓝牙钥匙](https://zeekrlife-resource-web.zeekrlife.com/pages/zeekr/user/enclosure-4.html)资料识别系列；IngeekDK、ZeekrVehicle 为名称推测，不确认车型、车主或钥匙状态。 |
| ROADBIT | [红点设计奖记录](https://www.red-dot.org/project/roadbit-e-bike-38409)记载 RoadBit 共享电动单车；广播名称只作中等置信度推测，不确认运营商、车型或骑行者。 |
| 云鲸、COLMO、小佩 | [云鲸配网说明](https://us.narwal.com/blogs/cleaning-guide/how-to-connect-robot-vacuum-to-wifi)、[COLMO 家电资料](https://www.colmo.com.cn/news-list/COLMO-double-wash-station)、[小佩官方设备说明](https://instructions.petkit.com/App%20Manual/CTW3/CT-W3_User%20Manual_EN.pdf)与明确名称共同识别系列；不把小佩 K3 猜成某种具体家电。 |
| 荣耀手环、OPPO 手表 | [荣耀 Band 6 用户指南](https://www.honor.com/content/dam/honor/sa-en/support/guidebook/wearables/honor-band6/HONOR%20Band%206%20User%20Guide%20%28ARG-B39%2Cen-gb%29.pdf)、[OPPO Watch 4 Pro 规格](https://www.oppo.com/cn/accessories/oppo-watch-4-pro/specs/)对应明确产品名称；不推断人员或健康数据。 |
| 米家温湿度计、S400 体脂秤、开关、灯具、Cariot 支架 | 温湿度计及体脂秤按[官方型号资料](https://www.mi.com/jp/product/xiaomi-smart-temperature-and-humidity-monitor-3/specs/)及[S400 规格](https://www.mi.com/tw/product/xiaomi-body-composition-scale-s400/specs/)识别；开关、灯具、手机支架采用[官方 MIoT 产品实例](https://miot-spec.org/miot-spec-v2/instances?status=all)中的具体型号与类型，不扩展到整个厂商命名空间。 |
| 中兴、斐讯、360 Wi-Fi，海尔无线模块 | IEEE 登记组织对应品牌无线接口；[360 官方说明书](https://ipc-pr-cdn.jia.360.cn/ipc-pr/LYQV6G.pdf)确认 360WiFi-* 默认名称。地址规则不确认具体设备型号。 |

新增名称、备注与设备说明均同步英语及简体中文。升级追加新系列与地址规则，保留自建条目、已有名称、备注、关闭状态及禁用规则；用户改为“全部规则同时满足”的 H3C／小米条目不追加互斥地址规则。历史观测原始记录保留记录时的分类；从特征库 92 起，界面和生成报告使用当前规则重新识别。

YD…、LEX…、TG、eg_ac_hanging、通用串口模块和匿名广播仍缺少可靠产品对应证据；论坛中的猜测未作为确定品牌规则写入。匿名设备及随机地址数量不等于独立物理设备数量。原始手机日志只保存在被忽略的本地验证目录，不随代码提交。

### 特征库 92：身份信息参与分类，详情与报告统一解析

- 完整 WPS 广播中的厂家和设备类型可以参与特征匹配，覆盖烽火及已核验的华为、小米等厂家名称。截断或格式错误的数据不作为身份依据；Realtek、Ralink 等平台名称不推断零售品牌。重复厂家名称的型号不再拼成重复身份。
- MiBeacon 的有效产品 ID 同时用于型号说明与分类，覆盖温湿度计、S400 体脂秤、水浸传感器和已知门锁。只使用已核验的产品映射；加密广播可以读取公开头部，但不会生成加密测量值。
- 设备详情、文本报告和 PDF 报告使用同一解析入口，补齐 WPS 型号编号、设备名、广播序列号、主设备类型和配置状态，以及可解析的米家明文对象。未提供广播数据、加密数据和暂不支持的解析分别说明。
- “未匹配”明确表示尚未命中特征规则，并不表示没有厂家或型号信息。规则编辑、导入导出及英语／简体中文说明同步支持新增规则。
- 历史观测的设备列表、路径、对比及生成报告按当前特征规则重新识别；不改写保存的原始记录。JSON 行日志重放恢复完整无线事实及多条广播记录，使旧日志也能使用新增身份规则。
- 升级保留自建特征、名称、备注和已有规则开关。被改为“全部规则同时满足”的内置条目不自动追加互斥身份条件。
- 解析依据：[MiBeacon 官方头部与对象定义](https://github.com/MiEcosystem/mijia_ble_common/blob/master/mible_beacon.h)、[xiaomi-ble 型号与明文对象实现](https://github.com/Bluetooth-Devices/xiaomi-ble/blob/main/src/xiaomi_ble/parser.py)、[ESPHome 小米广播解析实现](https://github.com/esphome/esphome/blob/dev/esphome/components/xiaomi_ble/xiaomi_ble.cpp)。WPS 厂家文字为设备自行广播的信息，不能验证设备真伪；通用型号和占位编号不作为精确零售型号结论。

### 特征库 93：未知样本分组及可用广播字段

以同一批 604 个类型／地址组合为基线，92 版未匹配 142 条（BLE 96、Wi-Fi 46）。这些是无线地址数量，随机地址可能属于同一台设备。

| 本轮识别范围 | 证据及限制 |
| --- | --- |
| 通用 HID 输入设备 | [Bluetooth SIG](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Assigned_Numbers/out/en/index-en.html) 将 1812 分配给 HID 服务；可识别服务类别，不确认键盘、遥控器等具体用途。YD 样本的外观值 0240 是通用钥匙圈，不能误称 HID 外观。 |
| Mixin Remote Control | 广播名称声明遥控用途，仅作名称推测，不把沁恒芯片当成整机品牌。 |
| iKF King Pro 系列耳机 | [iKF 官方产品](https://ikfaudio.com/products/ikf-king-pro-active-noise-cancelling-bluetooth-wireless-headphones-hi-res-audio)及原厂配对名称支持识别头戴耳机系列，不确认具体代际。 |
| Leapmotor_DigitalKey | 名称声明零跑数字钥匙用途，[零跑官方配置](https://cn.leapmotor.com/parameter-pk-web.html?carTypeId=24)有蓝牙钥匙功能；尚无该名称的官方绑定文档，因此显示为可能的名称识别，不确认车型或开锁状态。 |
| 银基 LEX 生态广播 | 同时核对名称、厂商编号和服务标识；[银基方案](https://www.ingeek.com/solution/boutique)支持数字钥匙平台背景，但不把 LEX 猜为雷克萨斯，不确认设备用途。 |
| Wi-Fi 接口供应商 | 按 [IEEE 登记表](https://standards-oui.ieee.org/oui/oui.csv)补充创维、友华、天邑康和、天翼终端、中移设备／物联网、杭州古北的实测前缀，以及现有 H3C、中兴、美的遗漏前缀。仅用于 Wi-Fi；供应商系列不确认整机品牌、型号或用途。 |
| Apple AWDL 连接广播 | 依据 [UxPlay 维护者说明](https://github.com/FDH2/UxPlay/wiki/Bluetooth_LE_beacon)，完整 16/8 TLV 是未公开内部格式的 AWDL 连接消息，不能称为 Nearby Info 或推断用户活动。协议识别不等于确认 Apple TV、手机或其他具体设备。 |

详情及文本／PDF 报告共用新增解析：AirPlay 接收端广播的 IPv4、显式 TCP 端口及基础标记；旧格式使用默认 7000 时明确标注默认值，不冒充广播字段。不连接或探测该地址。格式依据为 [原始 AirPlay 研究](https://github.com/furiousMAC/continuity/blob/master/messages/airplay_target.md)及上述 UxPlay 实现说明。

WPS 补充基础协议版本、配网锁定标记、选中注册方、射频频段和广播配网方法位掩码。依据 [hostap 定义](https://android.googlesource.com/platform/external/wpa_supplicant_8/+/refs/heads/main/src/wps/wps_defs.h)，字段均校验长度，未知值保留原始值；协议版本不是固件版本，方法声明不证明当前允许配网。

同批日志重放结果：未匹配由 142 降至 81（BLE 58、Wi-Fi 23），新增命中 61 条，没有原有命中变为未匹配。其中 13 条属于 HID／遥控器／耳机／可能数字钥匙用途分类；另外 48 条仅识别无线接口供应商、银基生态或 AWDL 协议，不能视为已确认具体产品。10 条 AirPlay 记录新增实际广播的 IPv4 和端口；WPS 新增版本 83 条、射频频段 49 条、方法位掩码 38 条、锁定标记 5 条、选中注册方 1 条。29 条 AWDL 记录只展示原始消息，不把原始字节算成已解出的状态。

剩余缺口：RZ-Slave 仅有杰理平台依据；eg_ac_hanging、MiCar、匿名 4669 暂无可靠产品对应；Keep 的 1818 私有广播不能套用功率测量特征布局；海尔广播与公开串口协议不同；BYD、AIMA 私有广播以及加密米家测量缺少可直接读取测量值的依据。美的身份字段见下方新增解析，不能据此生成空调运行状态。本轮不编造电量、车辆状态、空调状态或精确型号。供应商／协议级命中与具体产品识别分开解释。

### 特征库 94：实时未匹配样本及 Mesh 信标

2026-10-02 从手机导出的日志中，最近 15 分钟有 226 个类型／地址组合，93 版未匹配 35 条（BLE 24、Wi-Fi 11）。随机地址和同一设备的多个无线接口仍可能重复计数。

| 本轮识别范围 | 证据及限制 |
| --- | --- |
| 九号出行生态 | [维护者发现配置](https://raw.githubusercontent.com/BobMcGlobus/ha-ninebot/master/custom_components/ninebot_scooter/manifest.json)支持 424E／434E 自定义标识，SIG 分配的 0F1F 同时支持供应商识别；不根据序列名称猜车型，也不把全部设备称为滑板车。 |
| 杰理 SDK 协议 | 同时要求厂商编号、JLAISDK 数据标记和 AF30 服务。[杰理官方 SDK 文档](https://doc.zh-jieli.com/Apps/Android/bt_connect/zh-cn/master/development/interface_desc.html)支持标记含义，不能据此确认耳机、音箱或调音台。 |
| BLE_Joy_R 手柄用途 | [Powerwave 原厂说明书](https://powerwavegaming.com.au/wp-content/uploads/Joypad-Instructions-new-v1a-compressed.pdf)在 KeyLinker 更新流程中明确使用该名称；识别右侧手柄用途，不确认任天堂或具体零售品牌。 |
| Google FCF1 服务 | [SIG 分配表](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Assigned_Numbers/out/en/index-en.html)仅公布服务所属公司；不称为定位器、Fast Pair 或确定的手机型号。 |
| 蓝牙 Mesh 安全网络信标格式 | 严格解析完整 AD 结构、2B 类型、01 信标类型、22 字节正文及合法标记。[Mesh 标准 §3.10.3](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/MshPRT_v1.1/out/en/index-en.html)支持网络标识、IV 索引及认证原值字段；没有网络密钥，不宣称验证认证值，也不猜灯具或其他具体用途。 |
| 运营商及 Wi-Fi Direct 命名线索 | CMCC-四字符-5G 参考[移动家庭组网规范](https://oss.komect.com/openhomeres/2611dad71df9147c3ab965f4a740465ac/中国移动智慧家庭智能组网产品技术规范V4.4.1.pdf)，CU_ 前缀参考[网关管理厂家手册](https://wiki.mqrouter.com/docs/ITMS全光网管理-快速入门指南V2.2.pdf)，ChinaUnicom-MESH 为广播自报名称；均不验证运营商、用户合约或终端 OEM。DIRECT-xy 名称参考 [AOSP P2P 定义](https://android.googlesource.com/platform/external/wpa_supplicant_8/+/ea69e84/wpa_supplicant/README-P2P)，只作可能的协议命名识别，不确认 XMSv1 的成品用途。 |
| Wi-Fi 接口登记与芯片平台 | [IEEE 登记表](https://standards-oui.ieee.org/oui/oui.csv)支持 NEC、Micronet、诺信成、Beijing Lingji 及水星遗漏前缀；新接口规则只匹配原始前缀，不通过清除本地管理位推断供应商。Qualcomm／Realtek 厂商信息元素只支持平台线索，不代表整机品牌。 |

Mesh 解码与规则匹配共用校验入口，设备详情、文本和 PDF 报告使用相同字段。日志重放独立保留完整广播原文，不再用厂商数据正文代替整个 AD 包。严格 MAC 前缀在快速匹配和全部条件匹配中行为一致；原有 OUI 的虚拟 BSSID 规则不变。升级只追加本轮新条目及水星前缀，保留自建规则、禁用规则、删除的旧条件及 AND 配置。

同批最近 15 分钟日志重放：未匹配 35 降至 7（均为 BLE），新增命中 28 条，原有命中无丢失。1 条具有厂商手柄说明书用途依据；4 条是可能的运营商接入点命名，1 条是可能的 Wi-Fi Direct 命名；其余 22 条为供应商、生态或协议级部分识别，仍不能确认精确产品。6 条 Mesh 记录新增网络标识、IV 索引及原始标记／认证字段，认证值未验证。部分记录命中多个供应商／协议线索，命中家族数不等于设备数。

仍没有可靠用途依据的样本包括 MT-K3、eg_ac_hanging、XR、TL_GPSJLXW、BLE_DK 和未知 TI 服务。部分自定义厂商区装的是 MAC 地址或 ASCII 文字，不能把数值直接当 SIG 公司编号；含 EZVIZ 或 bl702l 的文字也不足以确认摄像头。MA-L、MA-M、MA-S 登记表未命中的地址不会猜测供应商。

### 已识别设备的可用信息增强

需求：对已识别设备继续解析有实际含义的广播信息，直接进入设备详情及文本／PDF 报告的字段或说明，并提供英语、简体中文。共用 `AdvPayloadDecoder.decodeDevice`，不修改用户填写的观察备注。本轮不改变匹配规则，特征库仍为 94。

- 路由器／接入点：WPS 配网方法从原始位掩码补充为按钮、实体／软件按钮、标签／显示／输入 PIN、NFC 等可读声明，保留原始掩码。声明能力不证明当前允许配网。WFA 设备类型补充电脑、输入设备、打印机／扫描仪、摄像机、存储、显示及音频等类别；保留未知类别、子类别和非 WFA 类型原值，现有接入点显示不变。依据 [AOSP WPS 定义](https://android.googlesource.com/platform/external/wpa_supplicant_8/+/android-6.0.1_r59/src/wps/wps_defs.h)。
- 接入点：新增 WMM 流量优先级及 U-APSD 客户端节能能力声明。完整验证信息／参数元素的子类型、版本、长度及参数保留字节；不以能力推断实测速度、延迟或客户端是否实际使用节能。依据 [AOSP WMM 定义](https://android.googlesource.com/platform/external/wpa_supplicant_8/+/master/src/common/ieee802_11_defs.h)。
- Bluetooth Mesh：将标志解释为“是否处于密钥刷新第 2 阶段”和“是否正在 IV 更新”，保持未认证声明。第 2 阶段标志未置位不能推断整个密钥刷新流程未进行。依据 [Mesh 标准 §3.10.3](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/MshPRT_v1.1/out/en/index-en.html)。
- Eddystone-TLM：完整的明文版本 0 遥测解析电池电压（mV）、信标自身温度（有符号 8.8）、已发送广播次数及开机／重启后的运行秒数。零电压和 `0x8000` 温度表示不支持测量；加密、未知版本或错误长度不生成测量值。依据 [Google 原始 TLM 格式](https://github.com/google/eddystone/blob/master/eddystone-tlm/tlm-plain.md)。

既有可用字段继续保留：明文米家温湿度／电量、体重秤测量、耳机电量及充电状态、WPS 厂家型号／序列号、AirPlay 广播 IPv4／端口等。当前没有匹配测量布局或密钥的九号、BYD、爱玛、美的、海尔等私有数据不能据此生成车辆状态、空调温度或设备电量。

同批 226 个类型／地址组合重放，103 条已识别记录获得新增字段：97 条接入点有 WMM／节能声明，22 条有可读配网方式，6 条有 Mesh 状态解释，数量存在重叠；原有分类无变化。Eddystone-TLM 明文解析已通过规范样例及边界测试，但这批样本没有 TLM 帧，未宣称获得实测遥测。

### 车辆／家电广播核验与米家无密钥解析

需求：继续核验九号、BYD、爱玛、美的、海尔实测广播，补充有依据的身份／测量字段；排查米家已识别但没有读数的原因。用户暂时没有绑定密钥，本轮只做无需密钥的被动解析。新增字段共用详情、分享文本及监测报告的解码入口，均提供英语与简体中文，保留自定义特征及观察备注；特征库仍为 94。

- 美的：严格识别 `0x06A8` 厂商数据中 `01 + 14 位字母数字短序列号`，显示广播短序列号及前 8 位 SN8 产品代码。SN8 不等于唯一零售型号。对实测的 11／13 字节地址帧，仅在 `01 ... 32 + 6 字节逆序地址` 与实际扫描地址一致时显示地址，不解释其余未知字节为运行状态。序列号帧与地址帧分别更新，避免同为 `01` 开头的记录互相覆盖。依据 [协议原作者的广播布局](https://github.com/midea-ble/midea-ble-go/blob/main/docs/protocol.md)、[美的官方 SN8/modelCode 定义](https://iot.midea.com/docs/control-midea-cloud-devices/cloud-2-cloud-thing-api.html)，并与实测广播核对。
- 米家：区分“本帧不含测量对象”和“加密测量需绑定密钥”，前者不代表读数为零。保留现有明文温湿度、电量等解析；发现帧不能替代测量帧，加密帧不能当成明文。新增已注册／绑定确认标志及广播连接、加密能力声明；能力和标志不证明实际连接或当前可绑定。
- 米家可选头部：按 `bondAbility = 3` 跳过并展示两字节 Wi-Fi MAC 后缀，再解析可选 I/O 信息，修正测量对象偏移。两字节 Mesh 尾部与对象分开，显示 PB-ADV／PB-GATT 配网传输声明、版本及原始状态，不猜测状态代码含义。依据 [小米官方头部定义](https://github.com/MiEcosystem/mijia_ble_common/blob/master/mible_beacon.h)及[广播构造实现](https://github.com/MiEcosystem/mijia_ble_common/blob/master/mible_beacon.c)。
- 车辆／海尔结论：九号短厂商数据、BYD 与爱玛的地址样式字段、海尔 FF01／FF02 数据尚无与本批样本对应的可靠遥测布局。九号公开实现中的电量／行驶数据来自主动认证后的请求响应，不是这些广播字节；海尔公开串口帧也不能套用于 BLE 数据。因此本轮不生成车辆电量、行驶状态或海尔温度。依据 [九号集成原作者的读取说明](https://github.com/BobMcGlobus/ha-ninebot)、[九号协议传输说明](https://nootnooot.codeberg.page/segway-ninebot-ble/transport/)。

验证覆盖：美的错误厂家／长度／字符／地址不一致，拆分记录更新，米家发现帧与密文不生成假读数，可选 Wi-Fi 后缀与 I/O 偏移，Mesh 尾部和测量对象共存，详情与报告翻译共用路径。完整原始样本、序列号、地址及位置仅保存在忽略的本地验证目录，不提交。

实测日志重放：最近同批 226 条类型／地址记录中 18 条获得新增字段（9 条美的、9 条米家），分类结果不变。完整日志合并为 605 条类型／地址记录后，13 条美的记录读出短序列号、SN8 及与扫描一致的广播地址；10 条有效米家记录的末次帧中，8 条不含测量对象、2 条为加密测量，3 条还包含 Mesh 信息。这些数量是广播记录，不等于物理设备数量，也不代表已经解出温湿度。496 项单元测试通过，Debug APK 构建成功并已覆盖安装到真实手机，应用成功启动；新字段实机逐项显示尚未完成验收。

### 位置寻踪优化：稳定信号与室内位置比较

需求：优化 BLE 寻踪的准确性和响应，强调美观界面、声振交互与真实手机检查。室内手机没有 GPS 时也能使用。此次落实第一批目标直接采样、稳定趋势、统一声振和 A/B 位置比较；不增加精确距离或方向箭头，不依赖 GPS 或地图。

- 目标 MAC、RSSI 和 Android `ScanResult.timestampNanos` 的实际观测时间在 BLE 回调中优先直接送入会话，再做载荷解析及入库，避开通用列表队列与匹配延迟。启动／重置后等待新广播，不把缓存信号当成新样本。只跟踪选定地址，不按同名或厂家自动拼接旋转地址。
- 中位数趋势比较要求每段至少 2 个有效样本并覆盖至少 700 毫秒。近期趋势窗口按实际收包间隔在 2～6 秒间适配，早期比较窗口同时延长；以中位绝对偏差评估波动，门槛至少 4 dB，并要求近期窗口前后两半都支持同一趋势。波动过大显示“不稳定”；取消固定 −45 dBm 即“非常近”的判定。
- 根据实机反馈保留空值行为：近 2 秒未收到目标广播时强度显示“—”，明确显示“未收到目标信号”，停止声振和增强／减弱指示，不保持旧数值、不补造中间帧。未收到广播不能判断发送端是否停发，页面不猜测未收到的原因。等待首帧、样本不足、波动过大与无近期信号分别处理。
- 画面、声音和振动统一使用最近有效窗口的中位信号，退出寻踪／切到后台后停止自动声振。底栏保留提示音和振动开关；物理听感和触感需要用户实测。
- 界面使用平滑信号环、趋势状态切换、样本质量、最近收到时间及信号曲线；下方为 A/B 位置比较。保留中英文、隐私演示地址遮蔽和竖向滚动，长文本／大字号可滚动操作。
- 室内流程：把门口、桌边等自选位置记为 A，静止采样；根据真实收包间隔自动选用 4～12 秒，并显示本次时长和进度。走几步到 B，以相同握持方式再次采样。只在样本量、时间覆盖、末次样本新鲜度及波动合格时比较。B 更强时可将 B 保留为新起点，再向另一位置比较；信号更强是寻找线索，不证明直线距离更近或设备就在该处。失败采样可重试，重新开始会清除会话及比较点。
- 测试包括目标隔离、旧样本／不可用 RSSI、异常跳值、波动、稀疏／集中突发、靠近／远离、失联、无 GPS 比较、采样失败恢复及保留较强起点。真实效果需同手机、同目标、同路径对照测试，不以单元测试宣称定位精度或寻找时间改善。

性能优先的单目标模式：寻踪期间 BLE 使用指定地址过滤及低延迟扫描，暂缓 Wi-Fi 主动扫描，其他设备不进入解析／匹配队列；普通设备提醒暂停，退出后恢复原强度、广域扫描和 Wi-Fi。寻踪时不按 70 秒周期或仅因目标安静而主动重启 BLE；明确扫描失败仍按原退避机制恢复。正常浏览的扫描恢复策略不变。无线零漏收不能保证，未声称获得真实方向或米数。旋转地址关联和可响铃标签留作后续。

验证：504 项单元测试全部通过，Debug APK 构建成功，并覆盖安装到三星真实手机；拉取已安装 APK 后 SHA-256 与本地产物一致。真机系统扫描记录确认指定地址过滤、LOW_LATENCY、零批量延迟；一次寻踪连续运行约 128 秒、收到 44 次目标回调，没有应用周期重启。页面确认实时信号、超过 2 秒后的空值与明确提示、A/B 采样及比较、重置清空和系统返回退出；退出后广域 BLE 和 Wi-Fi 扫描恢复，实时列表恢复。用户已确认声音和振动均有实际反馈。真机查看了中文暗色界面，模拟样本检查了英文亮色及大字号滚动；修正寻踪页重复系统留白，让首屏可见采样按钮。此次没有控制手机移动路径，A/B 信号差值仅用于核验交互，不作为寻找速度、距离准确性或无线零漏收的证据。原始手机截图、扫描记录及地址仅保存在忽略的本地验证目录，不提交。

后续界面与朝向需求：移除底部“测试声音与振动”按钮，底栏只保留声振开关；缩短信号环、曲线和说明占用，给比较点及采样进度留出空间。寻踪前台通过旋转向量传感器显示屏幕顶部在水平面相对磁北的朝向（角度与八方位），退出／后台注销监听，不依赖 GPS。A/B 点记录采样开始时的新鲜朝向，并随重采、保留 B、重置更新；没有有效朝向仍可采样信号。显示低精度状态，未取得传感器读数、过期或顶部几乎竖直而无法确定水平朝向时显示暂不可用，不生成虚假方向。采样点朝向是开始时的快照，非整个采样过程的平均方向，也非目标设备方向。实现依据 [Android 位置传感器和磁北朝向定义](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position)及旋转矩阵坐标变换。

本次验证：508 项单元测试通过，APK 构建及真机覆盖安装成功，已安装 APK 哈希与本地一致。手机原有字号和显示密度下，中文界面确认当前朝向、A/B 开始朝向、比较结果及采样按钮完整可见；采样期间进度、时长与按钮也未被底栏遮挡。实际 A/B 点已保存开始朝向，当前朝向变化时历史记录保持不变。手机系统记录确认传感器每 200 毫秒采样、进入后台注销，恢复前台重新注册并显示新鲜读数。未用外部罗盘校验绝对角度精度；不以显示正常或角度变化宣称磁北精度验收。真机截图及系统记录仅保存于忽略的本地目录。

界面细调：移除“只监听当前目标 · 无需 GPS”整行提示，保留设备标题和当前朝向；增大底栏上下留白，并在分隔线与提示音／振动开关之间留出 8 dp 间距。APK 构建及真机覆盖安装成功，实际截图确认提示行移除、底栏间距与主要内容完整显示。本次只调整界面，未新增室外 GPS 寻踪辅助。

朝向视觉提示：当前手机朝向增加明显的罗盘箭头，采用磁北固定在上、箭头随手机朝向旋转的统一刻度；A/B 点增加采样开始朝向的小箭头，保留角度和方位文字。跨越北向时沿最短角度平滑转动，朝向不可用时不画箭头，低精度箭头使用弱色并保留文字标记。箭头不代表目标设备方向，不增加定位约束或改变采样数据。APK 构建及覆盖安装成功，已安装包哈希一致；真实手机查看了当前朝向大箭头及 A 点开始朝向小箭头，位置与角度文字一致，采样按钮完整可见。截图仅保存于忽略的本地目录。
