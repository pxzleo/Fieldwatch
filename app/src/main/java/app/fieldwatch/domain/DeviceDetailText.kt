package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text dump of the device-detail screen. Same fields, no sparkline/presence art.
 * Not a legal identity.
 */
object DeviceDetailText {
    fun build(
        device: Sighting,
        signatureNames: List<String>,
        now: Long = System.currentTimeMillis(),
        attentionNotes: List<Pair<String, String>> = emptyList(),
        signatureNotes: List<Pair<String, String>> = emptyList(),
        fleets: List<Fleet> = emptyList(),
        translate: (String) -> String = { it },
        displaySignatureNames: List<String> = signatureNames,
    ): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        val facts = device.facts
        val title = device.listTitle(signatureNames, translate)
        val guess = DeviceExplain.guess(device, signatureNames, translate)
        val out = StringBuilder()

        fun line(label: String, value: String) {
            out.append(label).append(": ").append(value.trim()).append('\n')
        }
        fun section(title: String) {
            out.append('\n').append("## ").append(title).append('\n')
        }

        out.append(translate("Fieldwatch device detail\n"))
        out.append(iso.format(Date(now))).append('\n')
        out.append(
            translate("Experimental. Not a legal identity. Stock Android radios — this is what the OS exposed, not a guarantee a tracker or camera is present.\n"),
        )
        out.append('\n')
        out.append(title).append('\n')
        line("MAC", device.mac)
        if (device.name.isNotBlank()) line(translate("Advertised name"), device.name)

        out.append('\n')
        out.append(translate("What this looks like: ")).append(guess.headline).append('\n')
        out.append(guess.because).append('\n')
        if (attentionNotes.isNotEmpty()) {
            section(translate("Extra attention"))
            attentionNotes.forEach { (name, note) ->
                out.append(translate("EXTRA ATTENTION (%1\$s): ").format(name)).append(note.trim()).append('\n')
            }
            out.append(translate("Pattern match, not identity. Not a safety finding.\n"))
        }
        if (signatureNotes.isNotEmpty()) {
            section(translate("Notes"))
            signatureNotes.forEach { (name, note) ->
                out.append(name).append(": ").append(note.trim()).append('\n')
            }
        }

        section(translate("Identity"))
        line(
            translate("Radio"),
            if (device.kind == RadioKind.WIFI) {
                translate("Wi-Fi access point (beaconing a network)")
            } else {
                translate("Bluetooth Low Energy advertiser")
            },
        )
        line(translate("Address"), DeviceExplain.addressExplain(device, translate))
        vendorLine(device, translate)?.let { line(translate("Who made it"), it.replace('\n', ' ')) }
            ?: line(translate("OUI (vendor prefix)"), translate("%1\$s — no IEEE match; randomized addresses usually have none").format(device.oui))
        if (device.hiddenSsid) {
            line(translate("Network name (SSID)"), translate("Hidden — the AP is beaconing but not publishing a name"))
        }

        section(translate("Signal"))
        if (device.gone) {
            line(translate("How loud here (RSSI)"), translate("Not available"))
            val last = Rssi.lastMeasured(device.rssi, device.rssiHistory)
            line(translate("Last heard"), last?.let { "$it dBm" } ?: translate("Not available"))
        } else {
            line(translate("How loud here (RSSI)"), DeviceExplain.rssiExplain(device.rssi, translate))
            out.append(translate("Closer to 0 dBm is louder here, not a distance.\n"))
        }
        line(translate("Heard range this session"), Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory, translate))
        facts.txPowerDbm?.let {
            line(translate("Claimed transmit power"), translate("%1\$s dBm — how loud it says it transmits, not a distance").format(it))
        }
        if (device.channel != 0 || device.frequencyMhz != 0) {
            line(
                translate("Channel / frequency"),
                buildString {
                    if (device.channel != 0) append(translate("channel %1\$s").format(device.channel))
                    if (device.frequencyMhz != 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append("${device.frequencyMhz} MHz")
                    }
                    facts.channelWidth?.let { append(translate("  ·  %1\$s wide").format(it)) }
                },
            )
        }
        facts.wifiStandard?.let { line(translate("Wi-Fi generation"), it) }
        if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
            line(
                translate("Center frequencies"),
                listOfNotNull(
                    facts.centerFreq0?.let { "$it MHz" },
                    facts.centerFreq1?.let { "$it MHz" },
                ).joinToString("  ·  "),
            )
        }
        val rssiTail = device.rssiHistory.filter { Rssi.measured(it.rssi) }.takeLast(24)
        if (rssiTail.isNotEmpty()) {
            line(
                translate("Recent RSSI (oldest → newest)"),
                rssiTail.joinToString(", ") { it.rssi.toString() },
            )
        }

        if (device.kind == RadioKind.BLE) {
            section(translate("Bluetooth advertisement"))
            facts.primaryPhy?.let {
                val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                line(translate("Radio PHY"), phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy, translate) })
            }
            facts.connectable?.let {
                line(
                    translate("Connectable"),
                    if (it) translate("Yes — a phone could open a BLE connection")
                    else translate("No — broadcast-only (you can hear it, not join it from this scan)"),
                )
            }
            facts.advertisingIntervalMs?.let {
                line(translate("How often it advertises"), translate("%.0f ms between bursts (smaller = chattier on the air)").format(it))
            }
            facts.periodicIntervalMs?.let { line(translate("Periodic advertising"), "%.0f ms".format(it)) }
            facts.advFlags?.let { flags ->
                line(translate("Discoverability"), DeviceExplain.flagsExplain(flags, translate))
                line(translate("Flags (raw)"), "0x%02X".format(flags))
            }
            facts.appearance?.let { value ->
                val name = RadioDb.appearance(value)?.let(translate)
                line(
                    translate("What it says it is (Appearance)"),
                    name ?: translate("Unlisted Appearance 0x%04X").format(value),
                )
                line(translate("Appearance code"), "0x%04X".format(value))
            }
            CodDecoder.decodeOrNull(facts.deviceClass, translate)?.let { cod ->
                line(
                    translate("Classic Bluetooth class"),
                    buildString {
                        append(cod.major)
                        if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                        if (cod.services.isNotEmpty()) {
                            append(translate(". Also offers: "))
                            append(cod.services.joinToString(", "))
                        }
                    },
                )
            }
        }

        if (device.kind == RadioKind.WIFI) {
            section(translate("Wi-Fi access point"))
            facts.security?.let {
                line(translate("Encryption / login"), DeviceExplain.wifiSecurityExplain(it, translate))
                if (it.isNotBlank()) line(translate("Security string"), it)
            }
            facts.supportedRates?.let { line(translate("Supported rates"), translate("%1\$s Mbps  (* = required basic rate)").format(it)) }
            facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                line(translate("Capability string"), it)
            }
        }

        if (fleets.isNotEmpty() && (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI)) {
            val decoded = SignatureFieldDecoder.decodeSighting(device, fleets)
            if (decoded.isNotEmpty()) {
                section(translate("Decoded fields"))
                decoded.forEach { row ->
                    line(row.label, row.display)
                    if (row.note.isNotBlank()) line(translate("Note"), row.note)
                }
            }
        }

        if (device.serviceUuids.isNotEmpty()) {
            section(translate("Services it offers"))
            line(
                translate("Service IDs"),
                device.serviceUuids.joinToString("; ") { uuid ->
                    DeviceExplain.uuidGloss(uuid, translate)?.let { "$uuid  ·  $it" } ?: uuid
                },
            )
        }
        AdvPayloadDecoder.decodeDevice(device, translate).forEach { field -> line(field.label, field.value) }
        if (facts.serviceData.isNotEmpty()) {
            facts.serviceData.forEach { sd ->
                val named = RadioDb.serviceUuid(sd.uuid)?.let { " (${translate(it)})" } ?: ""

                line(
                    translate("Service data %1\$s%2\$s").format(uuidShort(sd.uuid), named),
                    sd.dataHex.hexSpaced().ifBlank { translate("(empty)") },
                )
            }
        }

        val mfg = facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let {
                listOf(MfgRecord(it, device.manufacturerDataHex))
            } ?: emptyList()
        }
        if (mfg.isNotEmpty()) {
            section(translate("Maker data inside the ad"))
            mfg.forEach { rec ->
                val company = RadioDb.company(rec.companyId) ?: translate("Not in the Bluetooth company list")
                line(translate("Bluetooth company 0x%04X").format(rec.companyId), company)

                if (rec.dataHex.isNotBlank()) {
                    line(translate("Raw payload (%1\$s bytes)").format(rec.dataHex.length / 2), rec.dataHex.hexSpaced())
                }
            }
        }

        if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
            section(translate("Wi-Fi vendor tags"))
            val rows = facts.vendorIes.ifEmpty {
                device.vendorIeOuis.map { VendorIeRecord(it, -1, "") }
            }
            rows.forEach { ie ->
                val protocol = WifiWpsDecoder.protocolName(ie)
                val org = protocol ?: RadioDb.vendorForOui24(ie.oui)
                val type = if (ie.type >= 0) translate(" type %d").format(ie.type) else ""
                line(
                    translate("Vendor OUI %1\$s%2\$s").format(ie.oui, type),
                    buildString {
                        append(org ?: translate("Unknown IEEE OUI"))
                        append(if (protocol != null) translate(" — Wi-Fi protocol tag, not the AP manufacturer.")
                            else translate(" — extra AP information element, not the SSID."))
                        if (ie.dataHex.isNotBlank()) {
                            append(" ")
                            append(ie.dataHex.hexSpaced())
                        }
                    },
                )

            }
        }

        section(translate("Session"))
        line(translate("First seen"), fmt.format(Date(device.firstSeen)))
        line(translate("Last seen"), fmt.format(Date(device.lastSeen)))
        line(translate("Hits"), device.hitCount.toString())
        Geo.screenCoord(device.latitude, device.longitude, false)?.let {
            line(translate("Last fix"), it)
            out.append(translate("Last fix is the phone’s GPS at hear-time, not a fix on this radio.\n"))
        }
        if (device.fleetIds.isEmpty()) line(translate("Matched signatures"),
            translate("No catalog signature matched; parsed identity fields may still be available."))
        if (device.fleetIds.isNotEmpty()) {
            line(translate("Matched signatures"), displaySignatureNames.joinToString("; ").ifBlank {
                device.fleetIds.joinToString("; ")
            })
        }
        if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
            line(translate("Raw advertisement"), device.rawHex.hexSpaced())
        }
        presenceLine(device, now, fmt, translate)?.let { line(translate("Presence (15 min)"), it) }
        return out.toString().trimEnd() + "\n"
    }

    private fun vendorLine(device: Sighting, translate: (String) -> String): String? {
        val parts = ArrayList<String>(3)
        device.vendor?.let {
            parts += translate("IEEE board/chip vendor: %1\$s (%2\$s). This is who owns the MAC prefix, not always the product brand.").format(it, device.oui)
        }
        val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
        if (mfgId != null) {
            val company = RadioDb.company(mfgId)
            parts += translate("Bluetooth company in the ad: %1\$s (0x%2\$04X).").format(company ?: translate("unlisted"), mfgId)
        }
        return parts.joinToString(" ").ifBlank { null }
    }

    private fun uuidShort(uuid: String): String {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
    }

    private fun presenceLine(device: Sighting, now: Long, fmt: SimpleDateFormat, translate: (String) -> String): String? {
        if (device.presence.isEmpty()) return null
        val from = now - 15 * 60 * 1000L
        val spans = device.presence.filter { (it.end ?: now) >= from }
        if (spans.isEmpty()) return null
        return spans.joinToString("; ") { span ->
            val start = fmt.format(Date(span.start.coerceAtLeast(from)))
            val end = span.end?.let { fmt.format(Date(it)) } ?: translate("now")
            "$start–$end"
        }
    }
}
