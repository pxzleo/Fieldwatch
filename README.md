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

### 非合作蓝牙目标：自动多点位置估计（1.1.18）

需求：以大量未知、非合作广播目标为主，不依赖配对或目标协作；自动判断可用定位条件，室外结合多个手机位置与 RSSI 计算可能设备位置并在地图标注，室内保留信号趋势和 A/B 比较。

实现：寻踪前台单独订阅 GPS，不更改普通扫描的位置标记设置；未授权、GPS 关闭、位置过期或精度不佳时显示具体原因并回退信号比较。用户可在寻踪地图申请精确定位。GPS 和 BLE 观测以单调时钟年龄换算接收时间，位置只匹配三秒内信号，位置误差超过 25 米不进入新测量点。原地及定位误差范围内重复接收归并，同一测量点保存信号中位数、波动、样本量、GPS 精度和首个有效朝向；只保留最近五分钟，最多 60 点、每点 120 个信号。退出或重置清空，不写入长期日志。

计算：未知参考功率与 1.5–4.5 环境衰减参数联合拟合，局部网格采用稳健残差和 GPS／信号波动权重。至少四个各有两次接收的测量点才尝试位置估计；范围不足、接近共线或强度变化不足时提示补测而不给目标坐标。拟合不一致时明确提示可能遮挡、移动或功率变化；候选接近搜索边界显示范围未闭合。计算每三秒在后台执行并可取消，不占用蓝牙接收回调。

界面：寻踪顶栏“地图”显示测量点数，独立地图保留主页面布局；显示手机位置、采样轨迹、信号强弱、最强接收点、候选位置十字和估计范围，可缩放／平移／点击采样点查看精度和朝向／重新居中。底图复用现有 OpenStreetMap 缓存及“在线地名和地图”开关，无法加载时明确显示相对位置图，计算仍在本地执行。隐私模式隐藏地图与估计坐标。英语和简体中文均覆盖新增界面。

能力判断：广播的可连接属性与手机 CS／UWB 硬件声明自动显示；未验证的目标测距能力保留为未知。当前第一批自动切换的是 GPS 多点估计与无 GPS 信号比较，没有新增对陌生目标的自动连接、绑定、响铃，也未启用 CS／UWB 会话。广播发射功率不当作一米参考 RSSI。目标地址变化不按同名合并。

边界：候选范围是模型近似，不是校准的概率置信区间；不能确定楼层。暂未收到广播不作为远距离证据。定位假定采样期间目标基本静止且发射条件稳定，室外实测精度、寻找速度和候选范围覆盖率需要用已知位置设备验证，不能由构建或模拟样本推断。

验证：514 项单元测试及正式 APK 构建通过，独立代码审查指出的乱序覆盖、逐样本过期、重置残留和拟合重复计算均已修复。模拟器检查了中英文／明暗色地图、在线底图、采样点查看、精确定位授权、平移／重新居中和重置清空。真机覆盖安装 1.1.18，安装包与本地 SHA256 一致；真实目标四次广播归并为一个点，显示 GPS 精度、采样朝向及补测提示，未在单点条件下给出目标坐标。已检查手机能力声明、底图与退出寻踪；没有进行控制路径的室外多点误差实测。QA 入口已移除，原始日志、截图与设备位置仅保存在忽略的本地验证目录。

### 双方兼容时的真实测距（1.1.19）

需求：手机与目标双方能力、权限和会话条件满足时使用真实测距；未知或不兼容目标继续被动寻踪。使用 Android 16（API 36）公开 Ranging API，最低系统仍为 API 29，旧系统显示接口不可用。手机能力使用测距服务的实际可用状态，不能仅凭硬件声明判定可测。

- BLE Channel Sounding：选中目标已绑定时自动连接检查 RAS 测距服务；未绑定目标由用户在“地图 → 方法与能力”中选择“连接并配对测距 / 重试”，先检查服务，再请求系统绑定。RAS 服务只是兼容线索，只有实际有效测距回调才显示米数。无需扫描或配对其他设备。
- UWB：目标须先准备与手机匹配的响应会话，并提供协商参数。在同一入口导入 JSON，字段为 `targetMac`（当前 BLE 目标地址）、`sessionId`（整数）、`configId`（单目标配置 1、3 或 6）、`localAddress` / `peerAddress`（不同的 2 或 8 字节十六进制地址）、`channel`、`preambleIndex`、`sessionKey`（配置 1 为 8 字节，3/6 为 16 或 32 字节十六进制密钥）、可选 `slotDuration`（1 或 2，默认 2）。参数还须符合手机报告的能力。导入文件不替代与目标的会话协商，也不证明对端已经启动；没有通用协议时不能仅凭 BLE 地址自动获取 UWB 密钥。配置仅用于本次寻踪，不写入设置或日志。
- 实测距离优先显示，声振根据实测距离加快或减慢；超过三秒、低质量或无效读数不继续作为距离使用，失败、停止和超时显示具体状态并回退 RSSI。地图位置估计、轨迹及 A/B 比较仍使用 GPS 与广播 RSSI，不能解释成真实测距三角定位。CS/UWB 当前不显示目标方向。
- 测距仅在寻踪前台运行；退出、后台、重置或更换目标关闭测距和 GATT、注销监听。目标及寻踪开始时间共同隔离回调。权限缺失、手机服务不可用、目标缺少 RAS、配对取消、无有效读数及系统原因代码均有显示。

接口依据：[Android Ranging API](https://developer.android.com/develop/connectivity/ranging)、[AOSP 测距与绑定条件](https://source.android.com/docs/core/connect/ranging-oob-spec-v3)。目前没有兼容对端的真实距离对照测量，不能由手机具备硬件、会话启动或单元测试宣称端到端测距准确性验收。

验证：518 项单元测试与 APK 构建通过，独立代码审查后补齐主页面回退原因、过期读数提示和文件导入启动会话绑定。三星真机覆盖安装 1.1.19，已安装 APK 与本地产物 SHA256 一致；实际测距服务报告 CS/UWB 可用。选中的 LYWSD03MMC 连接及服务发现完成后确认没有 RAS，显示具体原因、释放 GATT 并继续被动寻踪，未触发绑定或生成米数；重置清空，后台没有活动测距会话，退出后恢复普通扫描。API 29 模拟器启动和普通列表正常；模拟器原先仅有 16 MB Java 堆造成既有配置序列化内存不足，临时调整测试堆并使用独立测试用户后通过，未改变项目逻辑或手机数据。没有兼容对端验证 CS/UWB 实测回调与距离精度，也未完成真实 UWB 配置导入及对端响应会话验收。原始系统记录、截图和地址仅保存在忽略的本地验证目录。

### 列表中的测距设备标识（1.1.20）

需求：设备列表直接用绿色文字标明测距设备，帮助优先选择更适合定位的目标。所有共用设备列表行（含紧凑模式及类别展开行）和设备详情共用同一标识。英语与简体中文同步。

- “✓ 已验证 CS / UWB 测距”：本次扫描记录存续期间，该地址的对应会话确实返回过新鲜、有效、非低质量距离。退出寻踪、重置和后续暂时失联不抹去已验证能力，但标识不代表当前正在测距或保证定位速度；启动新的会话仍检查当前条件。
- “CS 测距服务 · 待验证”：广播服务 UUID / 服务数据声明了标准 RAS（0x185B），或当前目标连接服务发现实际提供了 RAS。只作为能力线索，不能代替双方条件与真实读数验证。
- 普通可连接属性、设备名称、品牌／类型及手机 CS/UWB 硬件能力不作为目标可测距证据；导入 UWB 配置也不会直接显示“已验证”。短 UUID 与标准完整 UUID 复用既有匹配规则，自定义 UUID 中碰巧出现 185B 不匹配。
- 无需逐个主动连接设备，广播线索在原有被动扫描中自动显示；连接发现和有效读数从现有寻踪会话同步回设备记录，严格保留目标／开始时间隔离。记录淘汰、清空或应用重启后不保留主动验证证据，不新增磁盘存储、配对或扫描任务。

验证：519 项单元测试全部通过、APK 构建成功，独立代码审查通过；测试覆盖 RAS 短／完整／服务数据 UUID、自定义 UUID 误匹配、普通可连接设备、低质量／过期／负值读数、后续广播保留标识、CS 与 UWB 验证及错误目标／旧会话拒绝。三星真机覆盖安装 1.1.20，安装包与本地 SHA256 一致，实时列表及类别展开正常，检查到的普通设备未显示测距标识。当前真机没有可用测距阳性目标，绿色标识的真实阳性设备与测距速度仍未验收。

### 苹果设备细分类（1.1.21）

需求：在实时列表、类别展开及设备详情／导出报告中区分有依据的苹果设备类别和耳机型号，英语与简体中文同步。共用现有广播解码，不新增连接、配对或扫描。

- Proximity Pairing 的标准 0x01 前缀、完整 25 字节载荷和产品代码识别 AirPods／Beats 型号；纠正 AirPods Max Lightning／USB-C、Beats Solo／Powerbeats／Flex／Studio／Fit 等旧映射。未知代码保留编号并显示型号未定，未核实的旧代码移除具体型号映射。型号信息优先于泛化的“Apple Device / Apple audio”匹配，用户命名继续保留。
- 完整的 Hey Siri 七字节帧按设备类别区分 iPhone、iPad、Mac、HomePod、Apple Watch；不据此推断某代手机、电脑或手表。Nearby Info 的活动码 0x0A 识别已佩戴且解锁的 Apple Watch，其他手表连接／自动解锁标志不作为发射设备是手表的证据。
- 通用 Continuity 显示“苹果设备（类型未定）”。Find My 广播不直接称作 AirTag；AirPlay 接收端不直接称作 HomePod／Apple TV。iBeacon／HomeKit 格式和苹果公司编号不能独立证明是苹果硬件。地址不跨记录合并，也不把地址数量当作物理设备数量。
- 列表增加独立类型行，使用自定义名称或仅显示广播名时仍可看到细分类；详情与报告使用同一结果及原有电量／佩戴／活动字段。只处理完整制造商记录的明确类别证据；截断 Siri、短 Nearby Info、未知类别与异常配对前缀不会生成具体类型。

依据：[Continuity 原始协议研究与设备类别解码](https://github.com/furiousMAC/continuity)、[CAPod 产品代码实现](https://github.com/d4rken-org/capod/tree/main/app/src/main/java/eu/darken/capod/pods/core/apple/ble/devices)、[Theengs AirPods 解码](https://github.com/theengs/decoder/blob/development/src/devices/APPLEAIRPODS_json.h)。广播字段是设备自报信息，仿冒设备可能使用相同产品代码；不代表硬件真伪认证。

验证：525 项单元测试与 APK 构建通过，独立审查发现的列表型号括号丢失、截断链报告误解码及短配对包冒认型号已修复。覆盖五类 Siri 设备、手表佩戴码与连接标志区分、纠正的十二项型号映射、次要制造商记录、未知代码与类型、损坏／短包、中英文报告。监测文本／PDF 共用解码路径，保存观测可复算。真机无线调试连接在本轮断开，尚未覆盖安装或完成真实界面验收；原始样本与构建日志留在忽略的验证目录。

### 强度排序与信号／用途细分类（1.1.22）

需求：实时列表能直接按信号强度、Wi-Fi／蓝牙及更细的无线类型排序，也可按设备用途排列；英语与简体中文同步。

- 实时页顶部常驻排序入口，支持即时最强、所选时间窗的平均最强、弱到强、Wi-Fi 优先、蓝牙优先、无线细分类、推测设备用途，以及最近收到／名称／已匹配优先。点击顶部排序切到全局列表，原有列表／带曲线列表保持布局；分类、雷达或时间线视图切到普通列表，避免只在原类别内部排序。原“显示”面板保留排序选项，分类视图明确标为“组内排序”。
- 无线细分类按 Wi-Fi 2.4／5／6 GHz／未知频段、BLE 可连接／仅广播／连接属性未知排列，依据实际频率和系统三态连接属性。当前扫描只包含 Wi-Fi 接入点和 BLE；不把 BLE 改称经典蓝牙，也不把连接属性未知当作不可连接。可连接不代表已配对或支持测距。
- 用途复用设备详情的同一证据排名，区分手机／平板、电脑、耳机／音频配件、音箱、穿戴设备、传感器、查找网络设备、信标、路由器／热点／接入点、车辆、输入／遥控设备、家居／门禁、摄像设备、无人机和健康设备等。通用苹果广播仍为用途未定，不按厂商或名称另起一套识别规则；用途是现有识别结果的推测，不是硬件确认。列表增加对应的无线类型与用途小字。
- 同组按已选择的即时／平均强度由强到弱，稳定记录键用于同分排序。强度缺失（包括 BLE 127）在强到弱和弱到强时都放最后，不冒充最强信号。新增排序设置保存，原配置缺少新字段仍使用既有默认值；不新增扫描、连接、配对或额外监听。

验证：532 项单元测试全部通过，APK 构建和独立代码审查通过。新增测试覆盖强弱双向／缺测值、平均窗口、频段与三态连接属性、类型内强度顺序、用途排序、通用苹果负例、旧配置兼容及排序保存。API 36 临时只读模拟器检查了中英文菜单、虚拟 Wi-Fi 2.4 GHz 分类、弱到强显示、类别视图切全局列表、用途选择和重启保留设置；模拟器没有真实 BLE 信号，多设备排列由混合样本单元测试验证。测试后关闭临时模拟器，未修改原模拟器磁盘或手机数据。真机无线调试仍未发现连接，尚未覆盖安装 1.1.22。

### 多设备实时扫描性能（1.1.23）

需求：大量设备实时扫描时，减少界面卡顿；不降低无线接收频率或寻踪采样，不改变缺测提示与分类规则。

- 实时数据由后台设备仓库完成分类，界面直接使用该结果。移除界面每次更新时在主线程重复匹配全部设备的路径；未选中设备或选中仍在列表中的设备时不再额外匹配。
- 已离开列表的选中设备仍保留实时同伴上下文，支持自定义群组规则。显示暂停时的重新分类和离线选中项匹配移到后台。
- 编辑器自动保存特征规则后在后台刷新设备仓库，即使停止扫描，已有设备的详情分类也会随规则更新。
- 平均强度用单次遍历计算，消除临时列表；同一轮排序按设备快照复用平均值。不同快照与新时间窗口重新计算，窗口边界、无测量值置后、即时强度和原有排序方向保持一致。

本地对照：900 台固定混合 Wi-Fi／BLE 样本，每台 40 条强度历史，预热 8 次、测量 15 次，使用默认特征库。同一进程内，旧界面全量重新分类中位数 21.70 ms，新路径无选中项不进行匹配，选中列表末项约 0.054 ms；平均强度排序的旧计算约 5.18 ms，新计算约 0.50 ms。仅代表电脑 JVM 的计算开销，不代表手机帧率；规则匹配算法和扫描参数没有修改，真机多设备场景仍需复测。

验证：536 项单元测试全部通过，APK 构建、差异检查和独立代码审查通过。新增覆盖平均窗口边界与缺测回退、同键不同快照及窗口更新、实时选择复用与离线同伴上下文、停止扫描后规则增删重新分类。临时性能测试保存于本地忽略的 QA 目录，未加入产品代码。无线调试没有已连接设备，本次尚未覆盖安装到真实手机。

### 历史有效身份与蓝牙服务归属（1.1.24）

需求：保留最后一次完整、有效的 WPS 厂家／型号等身份信息及记录时间，避免被后续缺字段的帧覆盖；补充 FDEE、FCC0、FD2D 的服务归属，详情及导出报告同步支持英语、简体中文。

- 每个地址只保存一份最后有效 WPS 身份快照。完整厂家和型号必须来自同一条正确解析的 WPS 元素；保存厂家、型号、型号编号、设备名、广播序列号等身份字段。新完整身份替换旧快照，后到的更早时间记录、截断／错误帧及缺字段帧不覆盖快照。
- 历史快照与当前原始广播分开保存。有快照时统一显示“上次有效 WPS 身份”字段及 UTC 记录时间，省去普通身份字段的重复显示；即使当前无线事实仍累计保留着旧元素，也不会误称为新收到的身份。旧快照中的配网状态、配置方法等动态字段不作为当前状态展示。Wi-Fi 缓存结果不会刷新身份记录时间。
- 新快照随观测、JSON 行日志和导出样本保存；原始 WPS 元素保持当前内容，用户名称和备注保持原值。没有新字段的旧 JSON 日志从已有完整身份字节恢复，时间明确标为“日志快照时间（非重新接收时间）”：旧日志可能累计保留元素，不能证明当时重新收到身份帧。仅存最后一帧、没有原始身份字节的旧记录无法凭空恢复。
- FDEE 说明为华为分配的服务；FCC0、FD2D 说明为小米分配的服务。接受准确的 16 位或 SIG 基础 UUID 别名，也读取服务数据中的 UUID；不从自定义 UUID 的子串匹配。不根据这些分配推断手机、家电、软总线版本、产品型号或工作状态，设备用途仍可为未确认。此次只补充字段，不修改识别规则库。
- 解析继续共用设备详情、分享文本、文本／PDF 监测报告入口。服务归属和历史字段标题均提供中英文，广播厂家、型号、序列号等原始值不翻译。

服务分配依据：[Bluetooth SIG Assigned Numbers](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Assigned_Numbers/out/en/index-en.html)。FDEE 的服务归属不能替代与 OpenHarmony 版本匹配的完整帧验证，本轮不解析私有遥测。

验证：546 项单元测试全部通过，APK 构建和独立代码审查通过。覆盖完整／缺字段／损坏 WPS 帧、时间顺序、缓存不刷新身份时间、新旧日志与序列化兼容、精确服务 UUID 及误匹配负例、中英文详情和监测报告。历史日志重放中，6 条最后合并记录缺少完整身份的设备找回了有效身份快照，另有 8 条设备记录获得服务归属字段；服务归属不等同于型号或用途识别。原始日志和审计输出保留在本地忽略的验证目录。无线调试当前没有已连接手机，尚未覆盖安装 1.1.24 或完成真机验收。

### 蓝牙寻踪反馈优化（1.1.25）

需求：优先改善普通、非合作 BLE 目标的寻踪，不新增 Wi-Fi 寻踪；更及时判断强弱趋势，保持无包时空显示和静音，不制造新的测量。

- 平滑强度取最后有效广播之前 2 秒内最多 5 个有效样本的中位数。没有新包时数值和波动保持不变，超过 2 秒没有新广播即清空；页面明确显示使用的样本数及上次收到时间。
- 趋势比较窗口锚定实际广播时间，不随界面时钟滚动产生新的接近／远离提示。窗口随广播间隔在 1.2～6 秒内调整，密集广播缩短比较等待，稀疏广播仍使用更长窗口；两半窗口须同向变化，继续保留噪声及时间覆盖检查。RSSI 表示相对强弱，不代表真实距离或目标方位。
- 声音和震动共用 2 秒有效期，独立检查当前时间，过期即停止反馈，不等待界面下一次刷新。有效期内仍使用现有强度节奏，未增加测试行或扩大界面。
- 寻踪计算移到后台；目标信号直达路径使用稳定的单调时钟映射，重复回调保留相同实际测量时间，普通设备列表和日志继续使用原有回调时间。重复样本提前返回，避免重复更新会话或 GPS 测量点。保持现有目标过滤、低延迟连续扫描及寻踪期间暂停其他监听的策略，不增加周期性扫描重启。

验证：550 项单元测试全部通过，APK 构建、差异检查和独立代码审查通过。新增覆盖无新包时数值／趋势不漂移、两秒过期、密集固定样本三秒内确认强弱变化、孤立尖峰／未来／缺测值、带 GPS 测量点的重复回调。约三秒的趋势确认仅为固定测试样本结果，不代表真实手机定位速度；无线调试当前未连接，尚未安装 1.1.25 或完成真机寻踪验收。

### 历史广播信息发掘（1.1.26）

需求：从已有数据继续发掘 BLE 有效字段、高频未知广播，以及 Wi-Fi 疑似同源接口；详情、分享、AI 分析提示和文本／PDF 报告使用相同结果，英语与简体中文同步。不新增 Wi-Fi 寻踪。

- BLE 按字段保留最后成功解码的原始来源及时间，而非只保留最后一包。无测量对象、加密读数、缺字段和损坏帧不能清除已有有效读数；较旧观测不能覆盖新字段。保留内容包括现有解析器支持的温湿度、电量、信标遥测、Mesh 等，不保证某个现场设备实际广播这些字段。
- 历史值统一显示“BLE 最后有效字段”，每行带时间；明确作为历史值，不当成当前电量或状态。原始来源随日志和样本保存，切换语言时重新解析。旧日志可能累计保存广播，因此恢复值的时间标为“日志快照（非重新接收时间）”。每个字段只有一个最新拥有来源，失去全部字段的来源不再保存；相同可解载荷复用字段列表以减少重复解析。
- Find Hub 服务帧必须包含完整的 20／32 字节临时标识，允许规范中的可选哈希标志字节。损坏长度会显示解析错误，不生成或覆盖历史 EID。新关联 UUID 在隐私模式的分享文本／AI 提示中也从 WPS 原始十六进制内容脱敏。
- 对 4669、FDEE、FF01／FF02 及两个完整 5810BBC0 私有服务 UUID 补充客观载荷字节长度、前两字节和未确认说明。FDEE 首字节不为 04 时明确提示不适用公开软总线 v4 布局，为 04 也不代表其余布局已经验证。不从未知字节推断版本、计数器、电量、锁状态或产品型号。
- 历史日志复查：4669 的 25 帧／7 条地址记录全部使用相同的 27 字节载荷；FDEE 的 10 帧／5 地址记录首字节为 01／05／06，均不能套用当前公开的版本 4 解码；InGeek CCFF 样本 010600 与相近 CCE4 的 010A01 均没有可靠公开的状态位含义。运行时共享载荷提示按当前详情／报告数据集计算，只说明相同捕获内容，不推断物理设备数量或合并地址。
- WPS UUID-E 必须来自完整、正确解析的 WPS 元素，长度为 16 字节且非全零；同一记录有互相矛盾的有效 UUID 不建立关联。相同 UUID 的地址显示“疑似同源 Wi-Fi 设备”，结合厂家／型号、广播序列号及观测时间范围重叠增强依据；同组任意已知身份字段冲突会明确提示未确认。UUID 可以复制，所有记录和原有设备计数保持独立。
- 详情及分享使用同一当前／回放记录集合，候选计算放在后台；报告列出全部窗口内 WPS 候选组，不受最强十二个 AP 列表限制。隐私模式隐藏新关联字段中的 UUID 并脱敏同伴地址。识别规则库、扫描频率及寻踪功能保持现有行为。

研究依据：[Bluetooth SIG Assigned Numbers](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Assigned_Numbers/out/en/index-en.html)、[OpenHarmony 软总线 BLE 版本与解析检查](https://github.com/openharmony/communication_dsoftbus/blob/master/core/discovery/ble/softbus_ble/src/disc_ble.c)、[软总线 BLE 字段位置](https://github.com/openharmony/communication_dsoftbus/blob/master/interfaces/kits/disc/disc_ble_constant_struct.h)、[InGeek PSA 产品资料](https://products.psacertified.org/products/ingeek-digital-lock-s1)、[Google Find Hub 广播结构表 15／16](https://developers.google.com/nearby/fast-pair/specifications/extensions/fmdn)。厂商提供数字钥匙平台不证明本次广播的用途或锁状态。原始地址、载荷及研究输出保留于忽略的本地验证目录，不提交至仓库。

验证：566 项正式单元测试全部通过，APK 构建、差异检查、需求符合性审查与独立代码质量审查通过。覆盖历史字段归属／时间顺序、相同来源快路、新旧日志与序列化、损坏 Find Hub 帧、中英文详情及报告、WPS 零值／短帧／冲突／跨元素身份负例、同伴地址及原始 UUID 隐私、未知服务精确 UUID 与重复记录计数。9595 行旧日志重放得到 605 条地址记录：304 条 BLE 记录保存有效历史字段，找回 1 条末帧已缺失的 Mesh 记录；29 条补充未知服务结构，21 条存在相同服务内容线索；WPS 有 13 组候选、涉及 26 地址，无已知身份冲突。此为字段保存及候选关联，不是新增确认了这些设备型号。临时重放测试已移出产品测试源；无线调试当前未连接，尚未安装 1.1.26 或完成真机界面验收。

### 实时排序精简（1.1.27）

需求：排序仅保留最强、Wi-Fi、蓝牙，并提供恢复初始状态入口。

- 实时页顶部与“显示”面板同步精简为三个排序选项及“恢复初始状态”。最强使用当前信号强度；Wi-Fi、蓝牙优先后，同类设备按当前强度由强到弱排列。选择排序进入全局列表，原列表／带曲线列表保留布局。
- 恢复初始状态恢复分类视图、默认的平均强度排序及 30 秒窗口；保留其他显示偏好、筛选条件和扫描数据。旧排序枚举继续兼容保存的配置。

验证：567 项单元测试通过，包括恢复排序只修改所需设置的回归测试；差异检查与独立代码审查通过。
