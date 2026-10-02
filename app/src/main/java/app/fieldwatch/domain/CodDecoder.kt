package app.fieldwatch.domain

/** Bluetooth Class of Device (Assigned Numbers). 24-bit CoD. */
object CodDecoder {
    data class Decoded(
        val major: String,
        val minor: String,
        val services: List<String>,
        val raw: Int,
    ) {
        fun summary(): String = buildString {
            append(major)
            if (minor.isNotBlank() && (raw and 0x3 != 0 || minorName((raw shr 8) and 0x1F, (raw shr 2) and 0x3F) { it } != "Uncategorized")) {
                append(" / ")
                append(minor)
            }
            if (services.isNotEmpty()) {
                append(" · ")
                append(services.joinToString(", "))
            }
        }
    }

    fun decode(cod: Int, translate: (String) -> String = { it }): Decoded {
        val format = cod and 0x3
        val minorBits = (cod shr 2) and 0x3F
        val majorBits = (cod shr 8) and 0x1F
        val serviceBits = (cod shr 13) and 0x7FF
        val major = majorName(majorBits, translate)
        val minor = if (format != 0) translate("format %1\$s").format(format) else minorName(majorBits, minorBits, translate)
        return Decoded(major, minor, serviceNames(serviceBits, translate), cod and 0xFFFFFF)
    }

    fun decodeOrNull(cod: Int?, translate: (String) -> String = { it }): Decoded? {
        if (cod == null || cod == 0) return null
        return decode(cod, translate)
    }

    private fun majorName(major: Int, translate: (String) -> String): String = when (major) {
        0x00 -> translate("Miscellaneous")
        0x01 -> translate("Computer")
        0x02 -> translate("Phone")
        0x03 -> translate("LAN / Network AP")
        0x04 -> translate("Audio / Video")
        0x05 -> translate("Peripheral")
        0x06 -> translate("Imaging")
        0x07 -> translate("Wearable")
        0x08 -> translate("Toy")
        0x09 -> translate("Health")
        0x1F -> translate("Uncategorized")
        else -> translate("Major 0x%02X").format(major)
    }

    private fun minorName(major: Int, minor: Int, translate: (String) -> String): String = when (major) {
        0x01 -> when (minor) {
            0x00 -> translate("Uncategorized")
            0x01 -> translate("Desktop")
            0x02 -> translate("Server")
            0x03 -> translate("Laptop")
            0x04 -> translate("Handheld PC/PDA")
            0x05 -> translate("Palm-size PDA")
            0x06 -> translate("Wearable computer")
            0x07 -> translate("Tablet")
            else -> translate("Computer 0x%02X").format(minor)
        }
        0x02 -> when (minor) {
            0x00 -> translate("Uncategorized")
            0x01 -> translate("Cellular")
            0x02 -> translate("Cordless")
            0x03 -> translate("Smartphone")
            0x04 -> translate("Wired modem / voice gateway")
            0x05 -> translate("Common ISDN access")
            else -> translate("Phone 0x%02X").format(minor)
        }
        0x03 -> when ((minor shr 3) and 0x7) {
            0 -> translate("Fully available")
            1 -> translate("1–17% utilized")
            2 -> translate("17–33% utilized")
            3 -> translate("33–50% utilized")
            4 -> translate("50–67% utilized")
            5 -> translate("67–83% utilized")
            6 -> translate("83–99% utilized")
            else -> translate("No service available")
        }
        0x04 -> when (minor) {
            0x00 -> translate("Uncategorized")
            0x01 -> translate("Wearable headset")
            0x02 -> translate("Hands-free")
            0x04 -> translate("Microphone")
            0x05 -> translate("Loudspeaker")
            0x06 -> translate("Headphones")
            0x07 -> translate("Portable audio")
            0x08 -> translate("Car audio")
            0x09 -> translate("Set-top box")
            0x0A -> translate("HiFi audio")
            0x0B -> translate("VCR")
            0x0C -> translate("Video camera")
            0x0D -> translate("Camcorder")
            0x0E -> translate("Video monitor")
            0x0F -> translate("Video display and loudspeaker")
            0x10 -> translate("Video conferencing")
            0x12 -> translate("Gaming / toy")
            else -> translate("A/V 0x%02X").format(minor)
        }
        0x05 -> {
            val sense = minor and 0x0F
            val hid = (minor shr 4) and 0x3
            val kind = when (sense) {
                0x00 -> translate("Uncategorized")
                0x01 -> translate("Joystick")
                0x02 -> translate("Gamepad")
                0x03 -> translate("Remote control")
                0x04 -> translate("Sensing device")
                0x05 -> translate("Digitizer tablet")
                0x06 -> translate("Card reader")
                0x07 -> translate("Digital pen")
                0x08 -> translate("Handheld scanner")
                0x09 -> translate("Handheld gestural input")
                else -> translate("Peripheral 0x%X").format(sense)
            }
            val extra = when (hid) {
                1 -> translate("keyboard")
                2 -> translate("pointing")
                3 -> translate("keyboard/pointing")
                else -> null
            }
            if (extra == null) kind else if (sense == 0) extra.replaceFirstChar { it.uppercase() } else "$kind + $extra"
        }
        0x06 -> buildList {
            if (minor and 0x08 != 0) add(translate("Display"))
            if (minor and 0x04 != 0) add(translate("Camera"))
            if (minor and 0x02 != 0) add(translate("Scanner"))
            if (minor and 0x01 != 0) add(translate("Printer"))
        }.joinToString(" + ").ifBlank { translate("Uncategorized") }
        0x07 -> when (minor) {
            0x01 -> translate("Wristwatch")
            0x02 -> translate("Pager")
            0x03 -> translate("Jacket")
            0x04 -> translate("Helmet")
            0x05 -> translate("Glasses")
            else -> translate("Wearable 0x%02X").format(minor)
        }
        0x08 -> when (minor) {
            0x01 -> translate("Robot")
            0x02 -> translate("Vehicle")
            0x03 -> translate("Doll / action figure")
            0x04 -> translate("Controller")
            0x05 -> translate("Game")
            else -> translate("Toy 0x%02X").format(minor)
        }
        0x09 -> when (minor) {
            0x01 -> translate("Blood pressure monitor")
            0x02 -> translate("Thermometer")
            0x03 -> translate("Weighing scale")
            0x04 -> translate("Glucose meter")
            0x05 -> translate("Pulse oximeter")
            0x06 -> translate("Heart / pulse rate monitor")
            0x07 -> translate("Health data display")
            0x08 -> translate("Step counter")
            0x09 -> translate("Body composition analyzer")
            0x0A -> translate("Peak flow monitor")
            0x0B -> translate("Medication monitor")
            0x0C -> translate("Knee prosthesis")
            0x0D -> translate("Ankle prosthesis")
            0x0E -> translate("Generic health manager")
            0x0F -> translate("Personal mobility device")
            else -> translate("Health 0x%02X").format(minor)
        }
        else -> if (minor == 0) "" else "0x%02X".format(minor)
    }

    private fun serviceNames(bits: Int, translate: (String) -> String): List<String> = buildList {
        if (bits and 0x001 != 0) add(translate("Limited Discoverable"))
        if (bits and 0x008 != 0) add(translate("Positioning"))
        if (bits and 0x010 != 0) add(translate("Networking"))
        if (bits and 0x020 != 0) add(translate("Rendering"))
        if (bits and 0x040 != 0) add(translate("Capturing"))
        if (bits and 0x080 != 0) add(translate("Object Transfer"))
        if (bits and 0x100 != 0) add(translate("Audio"))
        if (bits and 0x200 != 0) add(translate("Telephony"))
        if (bits and 0x400 != 0) add(translate("Information"))
    }
}
