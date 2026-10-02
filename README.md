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

新增名称、备注与设备说明均同步英语及简体中文。升级追加新系列与地址规则，保留自建条目、已有名称、备注、关闭状态及禁用规则；用户改为“全部规则同时满足”的 H3C／小米条目不追加互斥地址规则。历史观测保留记录时的分类，实时识别和日志重放使用当前规则。

YD…、LEX…、TG、eg_ac_hanging、通用串口模块和匿名广播仍缺少可靠产品对应证据；论坛中的猜测未作为确定品牌规则写入。匿名设备及随机地址数量不等于独立物理设备数量。原始手机日志只保存在被忽略的本地验证目录，不随代码提交。
