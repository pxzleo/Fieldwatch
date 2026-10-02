package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Paste-ready addendum prompt for Reports → AI Export.
 * Onboard Debrief is verbatim. Working data is rates, RSSI bands, Extra attention,
 * and finder-tag rows for a tracking stress-test — not a second inventory.
 */
object DebriefPrompt {
    const val WINDOW_SHORT_MS = 5 * 60_000L
    const val WINDOW_MS = 15 * 60_000L
    private const val MAX_CHARS = 90_000

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        now: Long = System.currentTimeMillis(),
        operatorPath: List<GpsSample> = emptyList(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        translate: (String) -> String = { it },
        displaySignatureNames: Map<String, String> = emptyMap(),
    ): String {
        val rawNames = fleets.associate { it.id to it.name }
        val names = rawNames + displaySignatureNames
        val displayFleets = fleets.map { it.copy(name = names[it.id] ?: it.name) }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val in15 = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
        val shortStart = maxOf(windowStart, windowEnd - WINDOW_SHORT_MS)
        val in5 = in15.filter { it.lastSeen >= shortStart || it.firstSeen >= shortStart }
        val wifi = in15.filter { it.kind == RadioKind.WIFI }
        val ble = in15.filter { it.kind == RadioKind.BLE }
        val signed = in15.filter { it.fleetIds.isNotEmpty() }
        val randomized = ble.count { it.randomized }
        val arrived = in15.filter { it.firstSeen >= windowStart }
        val departed = in15.filter { it.gone || it.lastSeen < windowEnd - 45_000L }
        val persistent = in15.filter { dwellMs(it, windowStart, windowEnd) >= win.durationMs * 2 / 3 }
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val extraHits = in15.flatMap { d ->
            d.attentionNotes(displayFleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val finders = in15.filter { TrackerMatch.kind(it, rawNames) == TrackerMatch.Kind.FINDER }
        val sigFamilies = signed.groupBy { d ->
            d.fleetIds.joinToString("+") { names[it] ?: it }
        }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
        val bleRssi = ble.map { it.rssi }
        val wifiRssi = wifi.map { it.rssi }
        val onboard = DebriefReport.build(devices, fleets, settings, operatorPath, now, places, win, customNames, observerNotes, bookmarkedKeys, translate = translate, displaySignatureNames = displaySignatureNames)
        val iso = utc(windowEnd)
        val start = utc(windowStart)

        val body = buildString {
            append(experimentalDisclaimerMarkdown(translate = translate))
            appendLine()
            appendLine(translate("You are a field RF analyst for the operator who collected this sit. Fieldwatch is a stock-Android, receive-only Wi-Fi access-point + BLE-advertiser listener."))
            appendLine()
            appendLine(translate("The **onboard Debrief** (verbatim below) already tabulated the sit: counts, Where you were, tracking callouts, inventories, Extra attention, takeaway. **Do not rewrite that report. Do not reprint inventories or stay lists.** Your job is an addendum the phone cannot write: rates, competing hypotheses, and a stress-test of the onboard tracking callouts."))
            appendLine()
            appendLine(translate("Constraints you must respect:"))
            appendLine(translate("- Hear-only. Wi-Fi rows are access points only. BLE rows are advertisers. Kind + MAC. BLE rotation is a new row and will not stitch."))
            appendLine(translate("- Signature / OUI / company matches are hypotheses, not identity, not a person or vehicle."))
            appendLine(translate("- GPS stamps (if present) are this phone at hear-time, not the other radio. Do not place a camera or tag at the GPS pin."))
            appendLine(translate("- Place names (if present) are system reverse-geocode of those stamps."))
            appendLine(translate("- RSSI is loudness at the phone, not meters."))
            appendLine(translate("- Live RAM cap is about 400 radios; unnamed BLE evicts after ~3 min. A named sit keeps more. This is not a complete capture."))
            appendLine(translate("- Do not claim a tracker is following unless the onboard GPS co-travel section supports it. A radio with you the whole sit is not automatically yours — it may be planted. Do not dismiss it. Do not invent a tail the onboard test did not flag. Do not treat a retail beacon as a Find My tail."))
            appendLine(translate("- A decoded live value on a tracking row is catalog text for that advertisement. Quote the catalog sentence when the onboard report includes one. Do not stitch that value onto a different MAC."))
            appendLine(translate("- An aircraft block and an amber track are positions the radio advertised. Trails with the same UAS id are one aircraft. They are not this phone's GPS and they are not a finding that the aircraft followed the operator."))
            appendLine(translate("- Do not give safety advice. Do not tell the operator they are safe or in danger."))
            appendLine(translate("- Treat this paste as operationally sensitive."))
            appendLine()
            appendLine(translate("## Your output (required — this is the addendum the operator reads)"))
            appendLine(translate("Write complete sentences. Headings as below. Short bullets only for Extra attention and tracking rows from the working table. No markdown tables. No code fences. No dump of the onboard inventories."))
            appendLine()
            appendLine(translate("1. **Disclaimer** — Repeat the experimental-use disclaimer first."))
            appendLine(translate("2. **What the onboard Debrief already established** — 3–5 sentences. Counts, distance, tracking callouts, Extra attention hits, Observer notes if any. Do not reprint inventories."))
            appendLine(translate("3. **What the numbers add** — 5- vs 15-minute counts, RSSI bands, RAND BLE percent, arrivals per minute, persistent vs gone, signature-family mix. Say street vs dwelling vs retail vs vehicle, and 5-minute vs 15-minute change (denser, quieter, stable). Confidence. If GPS ran, path length/span from the working table — do not pin a radio to a stay."))
            appendLine(translate("4. **Extra attention and tracking callouts** — Full identifiers from the working table (complete MAC, name, RSSI min/max, signatures, dwell). Stress-test onboard Possible trackers with you / Possible tail / Retail beacons / Wearables. Agree, qualify, or say the data are too thin. Pattern match, not identity. If none, say none."))
            appendLine(translate("5. **What another sit or Hunt would shrink** — Concrete in-app next steps only (Hunt on one Extra attention row, a longer GPS path, Compare sits, Filters). No safety advice. No “call the police.”"))
            appendLine()
            appendLine(translate("**Takeaway (required, last line).** One sentence starting with `Takeaway:` that adds *one number the onboard takeaway does not already say* (a rate, RAND percent, 5- vs 15-minute change, path span). Not a moral. Not a threat level."))
            appendLine()
            appendLine(translate("## Collection context"))
            appendLine(translate("- Tool: Fieldwatch (app.fieldwatch), receive-only, no association / injection / cloud."))
            appendLine(
                if (win.sitName != null) {
                    ReportText.format("- Window: sit **{0}** ({1} → {2} UTC), with a 5-minute recent slice.", translate, win.sitName, start, iso)
                } else {
                    ReportText.format("- Window: last **15 minutes** ({0} → {1} UTC), with a **5-minute** recent slice.", translate, start, iso)
                },
            )
            appendLine(ReportText.format("- Scan intensity: {0}. Stale after {1}s.", translate, translate(settings.intensity.name.lowercase()), settings.staleSec))
            appendLine(ReportText.format("- Location tags: {0}. Online place names: {1}.", translate, if (settings.tagLocation) translate("on") else translate("off"), if (settings.onlineLookup) translate("on") else translate("off")))
            appendLine()
            appendLine(translate("## Onboard Debrief (verbatim — already shown to the operator; do not rewrite)"))
            appendLine()
            appendLine(onboard.trimEnd())
            appendLine()
            appendLine(translate("## Working data (for the addendum — do not copy rosters into the answer)"))
            appendLine()
            appendLine(
                ReportText.format("15 min: Wi-Fi {0}  BLE {1}  signed {2}  hidden SSIDs {3}  ", translate, wifi.size, ble.size, signed.size, wifi.count { it.hiddenSsid }) +
                    ReportText.format("RAND BLE {0}/{1} ({2}%)  ", translate, randomized, ble.size, pct(randomized, ble.size)) +
                    ReportText.format("first-seen {0} ({1}/min)  persistent {2}  gone/quiet {3}", translate, arrived.size, perMin(arrived.size), persistent.size, departed.size),
            )
            appendLine(
                ReportText.format("5 min: Wi-Fi {0}  BLE {1}  ", translate, in5.count { it.kind == RadioKind.WIFI }, in5.count { it.kind == RadioKind.BLE }) +
                    ReportText.format("signed {0}  first-seen {1}", translate, in5.count { it.fleetIds.isNotEmpty() }, in5.count { it.firstSeen >= shortStart }),
            )
            appendLine(
                ReportText.format("BLE RSSI (n={0}): ≥−50 {1}  −51..−70 {2}  ", translate, ble.size, bandGe(bleRssi, -50), band(bleRssi, -70, -51)) +
                    "−71..−85 ${band(bleRssi, -85, -71)}  <−85 ${bandLt(bleRssi, -85)}",
            )
            appendLine(
                ReportText.format("Wi-Fi RSSI (n={0}): ≥−50 {1}  −51..−70 {2}  ", translate, wifi.size, bandGe(wifiRssi, -50), band(wifiRssi, -70, -51)) +
                    "−71..−85 ${band(wifiRssi, -85, -71)}  <−85 ${bandLt(wifiRssi, -85)}",
            )
            if (sigFamilies.isEmpty()) {
                appendLine(translate("Signature families: none."))
            } else {
                appendLine(translate("Signature families (count): ") + sigFamilies.take(12).joinToString { "${it.first}=${it.second}" })
            }
            appendLine(
                ReportText.format("GPS path: tagging {0}  ", translate, if (settings.tagLocation) translate("on") else translate("off")) +
                    ReportText.format("fixes {0}  length {1} m  span {2} m  ", translate, path.size, pathLen.toInt(), pathSpan.toInt()) +
                    ReportText.format("places {0}", translate, if (places.attempted) places.note else translate("off")),
            )
            appendLine()
            appendLine(translate("Extra attention:"))
            if (extraHits.isEmpty()) {
                appendLine(translate("- None."))
            } else {
                extraHits.forEach { (d, sig, note) ->
                    append("- ").append(row(d, names, now, windowStart, customNames, observerNotes, translate = translate))
                    append(" | ").append(sig).append(": ").append(note)
                    appendLine()
                }
            }
            appendLine()
            appendLine(translate("Observer notes:"))
            val observed = in15.mapNotNull { d ->
                val note = observerNotes[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                d to note
            }
            if (observed.isEmpty()) {
                appendLine(translate("- None."))
            } else {
                observed.sortedByDescending { it.first.rssi }.forEach { (d, note) ->
                    append("- ").append(row(d, names, now, windowStart, customNames, emptyMap(), translate = translate))
                    appendLine()
                    appendLine("  $note")
                }
            }
            appendLine()
            appendLine(translate("Finder-tag-like radios (for stress-test of onboard tracking; not a tail list):"))
            if (finders.isEmpty()) {
                appendLine(translate("- None."))
            } else {
                finders.sortedByDescending { it.rssi }.take(20).forEach { d ->
                    append("- ").append(row(d, names, now, windowStart, customNames, observerNotes, translate = translate))
                    append(translate(" rssiMin=")).append(d.rssiMin).append(translate(" rssiMax=")).append(d.rssiMax)
                    appendLine()
                }
            }
            appendLine()
            appendLine(translate("## End of working data"))
            appendLine(translate("Write the addendum now, following **Your output** at the top. Do not rewrite the onboard Debrief."))
        }
        return if (body.length <= MAX_CHARS) body
        else body.take(MAX_CHARS) + translate("\n\n[truncated for share-sheet size]\n")
    }

    fun experimentalDisclaimerMarkdown(translate: (String) -> String = { it }): String = FieldwatchDisclaimer.experimentalMarkdown(translate = translate)

    private fun row(
        d: Sighting,
        names: Map<String, String>,
        now: Long,
        windowStart: Long,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        translate: (String) -> String = { it },
    ): String = buildString {
        append(if (d.kind == RadioKind.WIFI) "WIFI" else "BLE")
        append(" ").append(d.mac)
        val label = d.reportName(customNames, translate).trim()
        if (label.isNotEmpty() && !label.equals(d.mac, ignoreCase = true)) {
            append("  ").append(label.take(32))
        }
        observerNotes[d.key]?.let { append(translate("  Observer: ")).append(it.take(80)) }
        append(translate(" rssi=")).append(d.rssi).append("dBm")
        if (d.randomized) append(translate(" RAND"))
        if (d.fleetIds.isNotEmpty()) {
            append(translate(" sig=")).append(d.fleetIds.joinToString("+") { names[it] ?: it })
        }
        val labels = d.liveDecode.reportLabels()
        if (labels.isNotEmpty()) append(translate(" decoded=")).append(labels.joinToString(","))
        val notes = d.liveDecode.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (notes.isNotEmpty()) append(translate(" decodeNote=")).append(notes.joinToString(" "))
        append(translate(" dwell=")).append(fmtDur(dwellMs(d, windowStart, now), translate = translate))
    }

    private fun dwellMs(d: Sighting, from: Long, to: Long): Long {
        var sum = 0L
        val spans = d.presence.ifEmpty { listOf(PresenceSpan(d.firstSeen, if (d.gone) d.lastSeen else null)) }
        for (span in spans) {
            val a = maxOf(span.start, from)
            val b = minOf(span.end ?: to, to)
            if (b > a) sum += b - a
        }
        return sum
    }

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun fmtDur(ms: Long, translate: (String) -> String = { it }): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) ReportText.format("{0}h{1}m", translate, m / 60, m % 60) else if (m > 0) ReportText.format("{0}m{1}s", translate, m, r) else ReportText.format("{0}s", translate, r)
    }

    private fun pct(n: Int, d: Int): Int = if (d <= 0) 0 else (n * 100) / d

    private fun perMin(n: Int): String {
        val rate = n / 15.0
        return if (rate >= 10) rate.toInt().toString() else "%.1f".format(Locale.US, rate)
    }

    private fun band(list: List<Int>, lo: Int, hi: Int) = list.count { it in lo..hi }
    private fun bandGe(list: List<Int>, lo: Int) = list.count { it >= lo }
    private fun bandLt(list: List<Int>, hi: Int) = list.count { it < hi }
}
