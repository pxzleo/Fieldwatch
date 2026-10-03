package app.fieldwatch

internal object UiV101Text {
    fun text(source: String): String = when (source) {
        "Apple Continuity radio (type 13)" -> UiText.text(R.string.catalog_v101_0)
        "A complete company 004C type 13 message with eight payload bytes identifies an Apple Continuity-format radio. The extended message does not establish an iPhone, iPad, Mac or Watch model, battery, or activity. Advertisements can be imitated." -> UiText.text(R.string.catalog_v101_1)
        "AirPrint printing service" -> UiText.text(R.string.catalog_v101_2)
        "A complete company 004C type 03 AirPrint message advertises a printing endpoint. AirPrint-compatible printers and print servers can use this Apple protocol; it does not prove Apple-manufactured hardware or an exact printer model. No print-job state is decoded." -> UiText.text(R.string.catalog_v101_3)
        "Huawei BLE device (type unconfirmed)" -> UiText.text(R.string.catalog_v101_4)
        "SIG company identifier 027D or a public IEEE-registered Huawei address identifies the advertised Huawei vendor. Phones, wearables, routers and other products share this vendor. Device type, model and operating state remain unconfirmed; randomized addresses are excluded from OUI evidence." -> UiText.text(R.string.catalog_v101_5)
        else -> source
    }
}

