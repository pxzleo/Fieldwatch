package app.fieldwatch

internal object UiV96Text {
    fun text(source: String): String = when (source) {
        "Midea advertised short serial" -> UiText.text(R.string.information_v96_0)
        "Midea SN8 product code" -> UiText.text(R.string.information_v96_1)
        "Midea readings status" -> UiText.text(R.string.information_v96_2)
        "Identity advertisement; no operating measurements decoded. SN8 does not confirm a unique retail model." -> UiText.text(R.string.information_v96_3)
        "Midea advertised BLE address (matches scan)" -> UiText.text(R.string.information_v96_4)
        "MiBeacon Wi-Fi MAC suffix (raw)" -> UiText.text(R.string.information_v96_5)
        "MiBeacon connectable capability (advertised)" -> UiText.text(R.string.information_v96_6)
        "MiBeacon encryption capability (advertised)" -> UiText.text(R.string.information_v96_7)
        "MiBeacon registered flag (advertised)" -> UiText.text(R.string.information_v96_8)
        "MiBeacon binding confirmation flag (advertised)" -> UiText.text(R.string.information_v96_9)
        "MiBeacon mesh provisioning transports (advertised)" -> UiText.text(R.string.information_v96_10)
        "None advertised" -> UiText.text(R.string.information_v96_11)
        "MiBeacon mesh state (raw)" -> UiText.text(R.string.information_v96_12)
        "MiBeacon mesh version" -> UiText.text(R.string.information_v96_13)
        "Truncated MiBeacon mesh information" -> UiText.text(R.string.information_v96_14)
        "MiBeacon readings status" -> UiText.text(R.string.information_v96_15)
        "Encrypted measurement payload; bindkey required to read values." -> UiText.text(R.string.information_v96_16)
        "This frame contains no measurement objects; not a zero reading." -> UiText.text(R.string.information_v96_17)
        else -> source
    }
}
