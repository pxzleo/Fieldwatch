package app.fieldwatch

internal object UiV100Text {
    fun text(source: String): String = when (source) {
        "DESSMANN door lock" -> UiText.text(R.string.catalog_v100_0)
        "LOCK_* name together with the DESSMANN registered address prefix F8:AA:B3 identifies a door-lock radio. The address-like manufacturer block is not a registered company identifier. Model and lock state are unknown." -> UiText.text(R.string.catalog_v100_1)
        "GREE air conditioner radio" -> UiText.text(R.string.catalog_v100_2)
        "GR-AC_* name and SIG company identifier 0D23 identify a Gree air-conditioner radio. The name suffix is not a verified retail model. No temperature or operating state is decoded." -> UiText.text(R.string.catalog_v100_3)
        "BOLOLO feeding appliance" -> UiText.text(R.string.catalog_v100_4)
        "BOLOLO-* advertised name points to the Bololo smart feeding appliance family. The advertisement does not distinguish a sterilizer, formula maker or another feeding appliance, or expose its operating state." -> UiText.text(R.string.catalog_v100_5)
        "UTRAO KH Ultra aquarium tester" -> UiText.text(R.string.catalog_v100_6)
        "UTRAO_KH_Ultra_* advertised name claims the KH Ultra aquarium alkalinity tester family. The suffix is a device code, not a KH reading. No water-quality measurement is decoded." -> UiText.text(R.string.catalog_v100_7)
        "Possible Bluetooth tape-light controller" -> UiText.text(R.string.catalog_v100_8)
        "The exact TAPE LIGHTS advertised name suggests a Bluetooth tape-light controller. Brand, model, brightness, color and power state remain unknown. The address prefix does not prove a Xerox product." -> UiText.text(R.string.catalog_v100_9)
        "JD JoyLink ecosystem radio" -> UiText.text(R.string.catalog_v100_10)
        "JoyLink name together with JD-assigned service FE70 identifies a JoyLink ecosystem radio. This platform is shared by different appliances; the advertisement does not identify an exact product or operating state." -> UiText.text(R.string.catalog_v100_11)
        else -> source
    }
}
