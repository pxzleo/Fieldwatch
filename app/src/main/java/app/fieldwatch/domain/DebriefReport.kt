package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DebriefSection(
    val number: String,
    val title: String,
    val body: String,
    val alert: Boolean = false,
)

data class DebriefPlaces(
    val attempted: Boolean,
    val available: Boolean,
    val note: String,
    val lines: List<String> = emptyList(),
    val namesByCell: Map<String, String> = emptyMap(),
) {
    /** Exact GPS cell, then nearest named cell within [maxM]. */
    fun nameNear(lat: Double, lon: Double, maxM: Double = 90.0): String? {
        namesByCell[Geo.cellKey(lat, lon)]?.let { return it }
        var best: String? = null
        var bestD = maxM
        for ((key, name) in namesByCell) {
            val parts = key.split(',')
            if (parts.size != 2) continue
            val klat = parts[0].toDoubleOrNull() ?: continue
            val klon = parts[1].toDoubleOrNull() ?: continue
            val d = Geo.meters(lat, lon, klat, klon)
            if (d < bestD) {
                bestD = d
                best = name
            }
        }
        return best
    }

    fun areaLine(): String {
        if (!attempted) return "off"
        val named = namesByCell.values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (named.isEmpty()) return note
        return named.joinToString(" · ")
    }

    companion object {
        val Off = DebriefPlaces(false, false, "off")
    }
}

data class ExtraAttentionHit(
    val signature: String,
    val radioLabel: String,
    val note: String,
)

data class DebriefDoc(
    val generatedUtc: String,
    val windowLine: String,
    val meta: List<Pair<String, String>>,
    val disclaimer: String,
    val trackingAlert: Boolean,
    val takeaway: String,
    val sections: List<DebriefSection>,
    val extraAttention: List<ExtraAttentionHit> = emptyList(),
    val heading: String = "FIELDWATCH FIELD DEBRIEF",
    val pdfKicker: String = "FIELD DEBRIEF",
    val pdfTitle: String = "Field debrief",
    val pathFigure: SitPathPlot.Figure? = null,
    val extraFigures: List<SitPathPlot.Figure> = emptyList(),
) {
    fun toPlainText(translate: (String) -> String = { it }): String = buildString {
        appendLine(heading)
        appendLine()
        appendLine(translate("DISCLAIMER"))
        appendLine(disclaimer)
        appendLine()
        meta.forEach { (k, v) -> appendLine("${k.padEnd(14)}$v") }
        appendLine()
        sections.forEach { sec ->
            appendLine("${sec.number}. ${sec.title.uppercase()}")
            appendLine(sec.body.trimEnd())
            appendLine()
        }
        appendLine("—")
        appendLine(ReportText.format("Takeaway: {0}", translate, takeaway))
    }

    fun withDemoMacs(macs: Collection<String>, demo: Boolean, translate: (String) -> String = { it }): DebriefDoc {
        if (!demo) return this
        fun t(s: String) = Geo.redactCoordsIn(MacUtil.redactMacsIn(s, macs, true), true)
        val note = translate("MAC tails (**:**:**) and GPS coordinates masked. Logs on the phone are unchanged.")
        return copy(
            meta = listOf(translate("Privacy") to note) + meta.map { it.first to t(it.second) },
            disclaimer = t(disclaimer),
            takeaway = t(takeaway),
            sections = sections.map { it.copy(title = t(it.title), body = t(it.body)) },
            extraAttention = extraAttention.map {
                it.copy(signature = t(it.signature), radioLabel = t(it.radioLabel), note = t(it.note))
            },
        )
    }
}

/**
 * Standalone field debrief (not an AI prompt). Heuristic sit report from
 * the last 15 minutes plus GPS co-travel of tracker-like radios.
 */
object DebriefReport {
    private const val WINDOW_MS = 15 * 60_000L
    private const val SHORT_MS = 5 * 60_000L
    private const val MOVE_M = 45.0
    /** Possible-tail extra gates. Own-kit uses a louder, longer “still here” window. */
    private const val COVER_FRAC = 0.5
    private const val FADE_DB = 12
    private const val TRAIL_LOUD_DBM = CoTravel.TRAIL_LOUD_DBM
    /** Own-kit “still here” — AirTags advertise slowly and rotate. */
    private const val OWN_HERE_MS = 180_000L
    private const val TAIL_HERE_MS = 20_000L
    private const val ON_BODY_MAX = -55
    private const val ON_BODY_MIN = -70

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        translate: (String) -> String = { it },
        displaySignatureNames: Map<String, String> = emptyMap(),
    ): String = document(
        devices, fleets, settings, operatorPath, now, places, window,
        customNames, observerNotes, bookmarkedKeys, watchedFleetIds, translate = translate,
        displaySignatureNames = displaySignatureNames,
    ).toPlainText(translate = translate)

    fun document(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        translate: (String) -> String = { it },
        displaySignatureNames: Map<String, String> = emptyMap(),
    ): DebriefDoc {
        val rawNames = fleets.associate { it.id to it.name }
        val names = rawNames + displaySignatureNames
        val displayFleets = fleets.map { it.copy(name = names[it.id] ?: it.name) }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val inWin = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
            .sortedByDescending { it.rssi }
        val wifi = inWin.filter { it.kind == RadioKind.WIFI }
        val ble = inWin.filter { it.kind == RadioKind.BLE }
        val named = inWin.filter { it.fleetIds.isNotEmpty() }
        val hidden = wifi.filter { it.hiddenSsid }
        val randomized = ble.count { it.randomized }
        val arrived = inWin.filter { it.firstSeen >= windowStart }
        val persistent = inWin.filter { dwellMs(it, windowStart, windowEnd) >= win.durationMs * 2 / 3 }
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val trackers = inWin.filter { TrackerMatch.kind(it, rawNames) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, rawNames, path, windowStart, windowEnd, TrackerMatch.Kind.FINDER, translate = translate, displayNames = names)
        val following = follow.filter { it.verdict == Verdict.FOLLOWING }
        val withYou = follow.filter { it.verdict == Verdict.MOVED_WITH_YOU }
        val ownLikely = follow.filter { it.verdict == Verdict.OWN_LIKELY }
        val wholeSit = ownLikely + withYou
        val beaconFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, rawNames) == TrackerMatch.Kind.BEACON },
            rawNames, path, windowStart, windowEnd, TrackerMatch.Kind.BEACON, translate = translate, displayNames = names,
        )
        val wearableFollow = followAssessments(
            inWin.filter { TrackerMatch.kind(it, rawNames) == TrackerMatch.Kind.WEARABLE },
            rawNames, path, windowStart, windowEnd, TrackerMatch.Kind.WEARABLE, translate = translate, displayNames = names,
        )
        val beaconsWithYou = stayedWithYou(beaconFollow)
        val wearablesWithYou = stayedWithYou(wearableFollow)

        val byCh = wifi.groupBy { it.channel }.toSortedMap()
        val networks = buildString {
            appendLine(ReportText.format("Heard {0} AP(s); {1} hidden SSID; {2} sat most of the window.", translate, wifi.size, hidden.size, persistent.count { it.kind == RadioKind.WIFI }))
            if (byCh.isNotEmpty()) {
                appendLine(translate("Channel occupancy:"))
                byCh.forEach { (ch, list) ->
                    val label = if (ch == 0) translate("unknown") else ReportText.format("ch {0}", translate, ch)
                    appendLine(ReportText.format("  {0} — {1} AP(s), strongest {2} dBm", translate, label, list.size, list.maxOf { it.rssi }))
                }
            }
            appendLine(translate("Loudest APs:"))
            wifi.take(12).forEach { d ->
                appendLine("  · ${wifiLine(d, names, windowStart, now, customNames, translate = translate)}")
                d.attentionNotes(displayFleets).forEach { (sig, note) ->
                    appendLine(ReportText.format("    extra attention ({0}): {1}", translate, sig, note))
                }
            }
            val associations = WpsAssociation.groups(wifi)
            if (associations.isNotEmpty()) {
                appendLine(translate("WPS association candidates:"))
                associations.forEach { group ->
                    appendLine("  · ${group.first().mac}")
                    WpsAssociation.fields(group.first(), group, translate, settings.demoMode).forEach { appendLine("    ${it.label}: ${it.value}") }
                }
            }
            if (hidden.isNotEmpty()) {
                appendLine(translate("Hidden SSIDs:"))
                hidden.forEach { appendLine(ReportText.format("  · {0}  {1}  {2} dBm  ch {3}", translate, it.mac, it.vendor ?: "", it.rssi, it.channel)) }
            }
        }
        val notable = ble.filter {
            inventoryKeep(it, settings, bookmarkedKeys) &&
                (it.fleetIds.isNotEmpty() || it.name.isNotBlank() || it.rssi >= -65 || it.manufacturerId != null)
        }.sortedByDescending { it.rssi }.take(20)
        val omittedRand = ble.count { !inventoryKeep(it, settings, bookmarkedKeys) }
        val bleBody = buildString {
            appendLine(ReportText.format("Heard {0} advertiser(s); {1} with randomized addresses; {2} signature-matched.", translate, ble.size, randomized, named.count { it.kind == RadioKind.BLE }))
            if (omittedRand > 0) {
                appendLine(ReportText.format("Unmatched rotating BLE omitted from lists ({0}). Counts include them. Sit export has every radio.", translate, omittedRand))
            }
            if (notable.isNotEmpty()) {
                appendLine(translate("Notable BLE:"))
                notable.forEach { d ->
                    val guess = DeviceExplain.guess(d, d.fleetIds.map { rawNames[it] ?: it }, translate = translate)
                    appendLine("  · ${bleLine(d, names, windowStart, now, customNames, translate = translate)}  |  ${guess.headline}")
                    d.attentionNotes(displayFleets).forEach { (sig, note) ->
                        appendLine(ReportText.format("    extra attention ({0}): {1}", translate, sig, note))
                    }
                    val decoded = SignatureFieldDecoder.decodeSighting(d, displayFleets)
                    if (decoded.isNotEmpty()) {
                        decoded.forEach { row ->
                            appendLine("    ${row.label}: ${row.display}")
                            if (row.note.isNotBlank()) appendLine("    ${row.note}")
                        }
                    } else {
                        d.liveDecode.forEach { chip ->
                            append("    ${chip.reportLabel()}")
                            if (chip.note.isNotBlank()) append("  ").append(chip.note)
                            appendLine()
                        }
                    }
                    val payloadFields = AdvPayloadDecoder.decodeDevice(d, translate)
                    payloadFields.distinct().forEach { appendLine("    ${it.label}: ${it.value}") }
                    BleServiceInspection.sharedFields(d, ble, translate).forEach { appendLine("    ${it.label}: ${it.value}") }
                }
            }
        }
        val serviceInspection = ble.flatMap { d -> d.facts.serviceData.flatMap { BleServiceInspection.fields(it, translate) } +
            BleServiceInspection.sharedFields(d, ble, translate) }.distinct()
        val serviceBody = if (serviceInspection.isEmpty()) "" else buildString {
            appendLine(translate("Unknown BLE service payloads:"))
            serviceInspection.forEach { appendLine("  ${it.label}: ${it.value}") }
        }
        val sigBody = buildString {
            if (named.isEmpty()) appendLine(translate("None in this window."))
            else {
                named.groupBy { it.fleetIds.joinToString("+") { id -> names[id] ?: id } }
                    .toList().sortedByDescending { it.second.size }
                    .forEach { (sig, list) ->
                        appendLine("${list.size}× $sig")
                        list.sortedByDescending { it.rssi }.take(8).forEach { d ->
                            append(ReportText.format("  · {0}  {1}  {2} dBm", translate, d.reportName(customNames, translate), d.mac, d.rssi))
                            val labels = d.liveDecode.reportLabels()
                            if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
                            appendLine()
                        }
                        list.flatMap { it.attentionNotes(displayFleets) }.distinct().forEach { (name, note) ->
                            appendLine(ReportText.format("  extra attention ({0}): {1}", translate, name, note))
                        }
                    }
            }
        }
        val persistBody = buildString {
            appendLine(ReportText.format("Sat most of this window: {0}", translate, persistent.size))
            persistent.filter { inventoryKeep(it, settings, bookmarkedKeys) }.take(15).forEach {
                appendLine(ReportText.format("  · {0}  {1}  dwell {2}", translate, it.reportName(customNames, translate), it.mac, fmtDur(dwellMs(it, windowStart, now), translate = translate)))
            }
            if (persistent.isEmpty()) appendLine(translate("  · None."))
            appendLine(ReportText.format("First seen in this window: {0} (loudest 8 below)", translate, arrived.size))
            arrived.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(8).forEach {
                appendLine(ReportText.format("  · {0}  {1}  {2} dBm", translate, it.reportName(customNames, translate), it.mac, it.rssi))
            }
        }
        val flags = anomalyLines(inWin, customNames, settings, bookmarkedKeys, translate = translate)
        val anomalyBody = if (flags.isEmpty()) {
            translate("No extra flags. Signature hits, Extra attention, and tracking callouts already cover named pattern matches.")
        } else flags.joinToString("\n") { "  · $it" }
        val attentionHits = inWin.flatMap { d ->
            d.attentionNotes(displayFleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val actionBody = actions(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, pathSpan, translate = translate)
            .joinToString("\n") { "  · $it" }

        val distanceLine = when {
            !settings.tagLocation -> translate("GPS tagging off — no path")
            path.size < 2 -> translate("GPS tagging on, fewer than 2 fixes in this window")
            else -> ReportText.format("traveled {0} along path · span {1} · {2} fixes", translate, fmtDist(pathLen, translate), fmtDist(pathSpan, translate), path.size)
        }
        val lookupLine = when {
            !places.attempted -> translate("off")
            places.namesByCell.isNotEmpty() -> places.areaLine()
            else -> places.note
        }
        val pictures = AircraftTrail.pictures(
            inWin.mapNotNull { d ->
                AircraftTrail.source(d, d.reportName(customNames, translate))
            },
            path,
        )
        val aircraftBody = AircraftTrail.body(pictures, translate = translate)
        var n = 1
        fun next() = (n++).toString()
        val sections = buildList {
            add(DebriefSection(next(), translate("Executive summary"), execSummary(wifi, ble, named, hidden, randomized, pathSpan, pathLen, following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, settings, places, win, translate = translate) + craftSentence(pictures, translate = translate)))
            add(DebriefSection(next(), translate("Where you were"), whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, windowEnd, customNames, bookmarkedKeys, translate = translate)))
            if (aircraftBody.isNotEmpty()) {
                add(DebriefSection(next(), translate("Aircraft"), aircraftBody))
            }
            observerNotesSection(inWin, customNames, observerNotes, translate = translate)?.let { body ->
                add(DebriefSection(next(), translate("Observer notes"), body))
            }
            add(
                DebriefSection(
                    next(),
                    translate("Tracking assessment"),
                    trackingSection(settings, path, pathSpan, pathLen, following, wholeSit, beaconsWithYou, wearablesWithYou, translate = translate),
                ),
            )
            if (wholeSit.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        translate("Possible trackers with you"),
                        trackerCallout(
                            translate("Finder tags (AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee) and loud pocket Apple BLE. ") +
                                translate("These radios stayed with your GPS path for this sit. ") +
                                translate("Fieldwatch cannot tell your own tag or phone from a tracker planted in the car, bag, or on you before you started. ") +
                                translate("Account for each MAC. Not a finding and not identity."),
                            wholeSit,
                            customNames, translate = translate,
                        ),
                        alert = true,
                    ),
                )
            }
            if (following.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        translate("Possible tail"),
                        trackerCallout(
                            translate("Finder tags that were not heard when this sit started, then stayed with your path. ") +
                                translate("That can mean someone started following you (their phone or tag), or a device was added during the trip. ") +
                                translate("Not a finding and not identity."),
                            following,
                            customNames, translate = translate,
                        ),
                        alert = true,
                    ),
                )
            }
            if (beaconsWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        translate("Retail beacons with you"),
                        trackerCallout(
                            translate("iBeacon / Minew / Estimote / Kontakt.io / Target Atrius basket radios that stayed with your GPS path. ") +
                                translate("Location beacons are usually fixtures in a store or venue — they do not typically move with you. ") +
                                translate("If one did, account for it (a Target basket you pushed, your own test tag, a badge, or a short path that still overlaps a fixture). ") +
                                translate("Not the same as a Find My tail. Not a finding and not identity."),
                            beaconsWithYou,
                            customNames, translate = translate,
                        ),
                        alert = true,
                    ),
                )
            }
            if (wearablesWithYou.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        translate("Wearables with you"),
                        trackerCallout(
                            translate("Garmin / Fitbit / Oura radios that stayed with your GPS path. ") +
                                translate("Watches and rings usually move with the person wearing them — often your own kit or someone walking with you. ") +
                                translate("They are not typically planted trackers. Account for each MAC. Not a finding and not identity."),
                            wearablesWithYou,
                            customNames, translate = translate,
                        ),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), translate("Environment"), environment(wifi, ble, randomized, persistent, pathSpan, pathLen, translate = translate)))
            add(DebriefSection(next(), translate("Networks (Wi-Fi access points)"), networks.trimEnd()))
            add(DebriefSection(next(), translate("Bluetooth LE"), (bleBody + serviceBody).trimEnd()))
            add(DebriefSection(next(), translate("Signature hits"), sigBody.trimEnd()))
            add(DebriefSection(next(), translate("Persistence"), persistBody.trimEnd()))
            if (attentionHits.isNotEmpty()) {
                add(
                    DebriefSection(
                        next(),
                        translate("Extra attention"),
                        buildString {
                            appendLine(translate("Pattern match, not identity, not a skimmer detector, not a safety finding."))
                            attentionHits.forEach { (d, sig, note) ->
                                appendLine(ReportText.format("  · {0}  {1}  {2} dBm  [{3}]", translate, d.reportName(customNames, translate), d.mac, d.rssi, sig))
                                appendLine("    $note")
                            }
                        }.trimEnd(),
                        alert = true,
                    ),
                )
            }
            add(DebriefSection(next(), translate("Anomalies"), anomalyBody))
            add(DebriefSection(next(), translate("Privacy"), privacy(wifi, ble, randomized, hidden, settings, places, pictures.isNotEmpty(), translate = translate)))
            add(DebriefSection(next(), translate("Recommended actions"), actionBody))
        }

        val windowLine = if (win.sitName != null) {
            ReportText.format("sit {0} ({1} → {2} UTC)", translate, win.sitName, utc(windowStart), utc(windowEnd))
        } else {
            ReportText.format("last 15 minutes ({0} → {1} UTC)", translate, utc(windowStart), utc(windowEnd))
        }
        val heading = if (win.sitName != null) {
            ReportText.format("FIELDWATCH SIT — {0}", translate, win.sitName)
        } else {
            translate("FIELDWATCH FIELD DEBRIEF")
        }
        val meta = buildList {
            add(translate("Generated") to ReportText.format("{0} UTC", translate, utc(now)))
            if (win.sitName != null) add(translate("Sit") to win.sitName)
            add(translate("Window") to windowLine)
            add(translate("Radios") to "${inWin.size}")
            add(translate("Tool") to translate("Fieldwatch (app.fieldwatch) · stock Android · receive-only Wi-Fi AP + BLE advertiser"))
            add(translate("Scan") to ReportText.format("{0} · stale {1}s · brief hold {2}s", translate, translate(settings.intensity.name.lowercase()), settings.staleSec, settings.decaySec))
            add(translate("GPS tag") to if (settings.tagLocation) translate("on") else translate("off"))
            add(translate("Distance") to distanceLine)
            add(translate("Places") to lookupLine)
            add(
                translate("Classification") to if (pictures.isNotEmpty()) {
                    translate("Operationally sensitive — neighbor SSIDs, MACs, operator GPS, advertised aircraft track")
                } else {
                    translate("Operationally sensitive — neighbor SSIDs, MACs, operator GPS")
                },
            )
        }
        return DebriefDoc(
            generatedUtc = utc(now),
            windowLine = windowLine,
            meta = meta,
            disclaimer = FieldwatchDisclaimer.report(win, translate = translate),
            trackingAlert = following.isNotEmpty() || ownLikely.isNotEmpty() || withYou.isNotEmpty(),
            takeaway = takeaway(following, withYou, ownLikely, beaconsWithYou, wearablesWithYou, pathSpan, settings, named, translate = translate),
            sections = sections,
            extraAttention = attentionHits.map { (d, sig, note) ->
                ExtraAttentionHit(
                    signature = sig,
                    radioLabel = ReportText.format("{0}  {1}  {2} dBm", translate, d.reportName(customNames, translate), d.mac, d.rssi),
                    note = note,
                )
            },
            heading = heading,
            pdfKicker = if (win.sitName != null) translate("SIT") else translate("FIELD DEBRIEF"),
            pdfTitle = if (win.sitName != null) ReportText.format("Sit — {0}", translate, win.sitName) else translate("Field debrief"),
            pathFigure = AircraftTrail.applyWalk(
                pathFigure(
                    win.sitName ?: translate("Last 15 minutes"), path, inWin, displayFleets,
                    customNames, observerNotes, bookmarkedKeys, watchedFleetIds, translate = translate,
                ),
                pictures,
                secondary = false, translate = translate,
            ),
            extraFigures = AircraftTrail.ownFigures(pictures, translate = translate),
        )
    }

    private fun pathFigure(
        title: String,
        path: List<GpsSample>,
        devices: List<Sighting>,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        translate: (String) -> String = { it },
    ): SitPathPlot.Figure? {
        val path = Geo.despikePath(path)
        if (path.size < 2) return null
        val plot = SitPathPlot.dotsFrom(
            devices, fleets, namedKeys = customNames.keys,
            customNames = customNames, observerNotes = observerNotes,
            bookmarkedKeys = bookmarkedKeys,
            watchedFleetIds = watchedFleetIds,
            alertsOnly = true,
        )
        return SitPathPlot.Figure(
            kicker = translate("OPERATOR PATH"),
            tracks = listOf(SitPathPlot.FigureTrack(title, path)),
            dots = plot.points,
            lengthM = Geo.pathLengthM(path),
            spanM = Geo.spanM(path),
            caption = ReportText.format("North-up. Line is this phone ({0}). A MAC alert or a signature alert is drawn once. A decoded latitude and longitude is the last advertised position. Anything else is the strongest hear. A number is that place (Path key).", translate, path.lengthM(translate)),
        )
    }

    private fun List<GpsSample>.lengthM(translate: (String) -> String = { it }): String {
        val m = Geo.pathLengthM(this)
        return if (m >= 1000) ReportText.format("{0} km", translate, "%.1f".format(Locale.US, m / 1000))
        else ReportText.format("{0} m", translate, m.toInt())
    }

    /**
     * GPS / places / co-travel block for the AI Export prompt. Same heuristics
     * as the field debrief; markdown so a chat model can cite it.
     */
    fun gpsAnalystMarkdown(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        operatorPath: List<GpsSample>,
        now: Long = System.currentTimeMillis(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        translate: (String) -> String = { it },
    ): String = buildString {
        val names = fleets.associate { it.id to it.name }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val inWin = devices.filter { it.lastSeen >= windowStart || it.firstSeen >= windowStart }
        val trackers = inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER }
        val follow = followAssessments(trackers, names, path, windowStart, windowEnd, TrackerMatch.Kind.FINDER, translate = translate)
        val beaconsMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.BEACON },
                names, path, windowStart, windowEnd, TrackerMatch.Kind.BEACON, translate = translate,
            ),
        )
        val wearablesMd = stayedWithYou(
            followAssessments(
                inWin.filter { TrackerMatch.kind(it, names) == TrackerMatch.Kind.WEARABLE },
                names, path, windowStart, windowEnd, TrackerMatch.Kind.WEARABLE, translate = translate,
            ),
        )

        appendLine(translate("## Where you were (operator GPS)"))
        appendLine(ReportText.format("- Tag detections with GPS: {0}.", translate, if (settings.tagLocation) translate("on") else translate("off")))
        appendLine(
            translate("- Online place names: ") +
                if (places.attempted) places.note
                else translate("off (Settings → Online place names in Debrief). No reverse-geocode this export."),
        )
        append(whereYouWere(settings, path, pathLen, pathSpan, inWin, names, places, windowEnd, customNames, bookmarkedKeys, translate = translate).trimEnd())
        appendLine()
        appendLine()
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine(translate("- Following test: insufficient movement (need ~45 m span). Do not infer a tail."))
            appendLine()
        }
        appendLine(translate("## GPS co-travel"))
        appendLine(
            translate("Only radios that stayed with the operator path are listed. ") +
                translate("House tags and other radios the operator only passed are omitted — they are not tracking. ") +
                translate("Not identity. Find My MAC rotation will not stitch a tail that changes address. ") +
                translate("Possible tail extra gates (walks): trail covers ≥ half the operator path, ") +
                translate("≥ 2/3 of GPS stamps at −75 dBm or louder, last stamp not 12 dB below loudest. ") +
                translate("Fail any one → omit (pass-by), not a tail. ") +
                translate("Finder tags (AirTag / SmartTag / Tile / Chipolo / Pebblebee / Find My / loud pocket Apple) ") +
                translate("are the tracking test. Retail beacons and wearables that co-travel are listed separately — ") +
                translate("they do not typically move with you (beacons) or are usually own kit (wearables)."),
        )
        val followingMd = follow.filter { it.verdict == Verdict.FOLLOWING }
        val wholeSitMd = follow.filter {
            it.verdict == Verdict.OWN_LIKELY || it.verdict == Verdict.MOVED_WITH_YOU
        }
        if (followingMd.isEmpty() && wholeSitMd.isEmpty() && beaconsMd.isEmpty() && wearablesMd.isEmpty()) {
            appendLine(translate("- None stayed with the path."))
        } else {
            fun dump(title: String, rows: List<FollowHit>) {
                if (rows.isEmpty()) return
                appendLine()
                appendLine("### $title")
                rows.forEach { h ->
                    val d = h.device
                    appendLine(
                        ReportText.format("- {0}  {1}  {2}  RSSI {3} dBm ", translate, h.label, d.reportName(customNames, translate), d.mac, d.rssi) +
                            ReportText.format("(min {0} / max {1})  trail {2} fixes, span {3} m", translate, d.rssiMin, d.rssiMax, h.samples, h.spanM.toInt()),
                    )
                    appendLine("  ${h.detail}")
                }
            }
            dump(
                translate("Possible trackers with you (finder tags, whole sit — yours or planted before you started)"),
                wholeSitMd,
            )
            dump(
                translate("Possible tail (finder tags, first heard after this sit started, then stayed)"),
                followingMd,
            )
            dump(
                translate("Retail beacons with you (iBeacon / Minew / Estimote / Kontakt.io / Target Atrius basket — fixtures; a pushed cart will co-travel)"),
                beaconsMd,
            )
            dump(
                translate("Wearables with you (Garmin / Fitbit / Oura — usually own kit or a companion)"),
                wearablesMd,
            )
        }
    }

    private enum class Verdict { FOLLOWING, MOVED_WITH_YOU, OWN_LIKELY, STATIONARY, INSUFFICIENT }

    private data class FollowHit(
        val device: Sighting,
        val label: String,
        val verdict: Verdict,
        val detail: String,
        val spanM: Double,
        val samples: Int,
    )

    private fun stayedWithYou(hits: List<FollowHit>): List<FollowHit> =
        hits.filter {
            it.verdict == Verdict.FOLLOWING ||
                it.verdict == Verdict.MOVED_WITH_YOU ||
                it.verdict == Verdict.OWN_LIKELY
        }

    private fun followAssessments(
        trackers: List<Sighting>,
        names: Map<String, String>,
        operatorPath: List<GpsSample>,
        windowStart: Long,
        now: Long,
        kind: TrackerMatch.Kind,
        translate: (String) -> String = { it },
        displayNames: Map<String, String> = emptyMap(),
    ): List<FollowHit> {
        val opSpan = Geo.spanM(operatorPath)
        val opLen = Geo.pathLengthM(operatorPath)
        return trackers.map { d ->
            val label = TrackerMatch.label(d, names, displayNames, translate)
            val trail = d.gpsTrail.filter { it.at >= windowStart }
            val span = Geo.spanM(trail)
            val trailLen = Geo.pathLengthM(trail)
            val presentAtStart = d.firstSeen <= windowStart + 15_000L
            val stillHere = now - d.lastSeen <= TAIL_HERE_MS
            val ownHere = now - d.lastSeen <= OWN_HERE_MS
            val onBody = d.rssiMax >= ON_BODY_MAX && d.rssiMin >= ON_BODY_MIN && trail.size >= 2
            val cover = opLen > 0.0 && trailLen >= COVER_FRAC * opLen
            val (verdict, detail) = when {
                operatorPath.size < 2 || opSpan < MOVE_M ->
                    Verdict.INSUFFICIENT to ReportText.format("Operator GPS path too short ({0} m) to test following.", translate, opSpan.toInt())
                trail.size < 2 ->
                    Verdict.INSUFFICIENT to translate("Heard, but not at two GPS points. Cannot test co-travel.")
                onBody && ownHere ->
                    Verdict.OWN_LIKELY to onBodyLine(kind, d, trail.size, translate = translate)
                cover && ownHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        ReportText.format("Heard along {0} m of your {1} m path and still loud ({2} dBm). ", translate, trailLen.toInt(), opLen.toInt(), d.rssiMax) +
                        withYouNote(kind, d, translate = translate)
                span < MOVE_M * 0.6 ->
                    Verdict.STATIONARY to ReportText.format("Heard near one place ({0} m span) while you moved {1} m. Looks stationary — you walked away from it.", translate, span.toInt(), opSpan.toInt())
                presentAtStart && stillHere && d.rssiMax >= ON_BODY_MAX ->
                    Verdict.OWN_LIKELY to
                        ReportText.format("Moved {0} m with you, already on the air when this 15-minute window opened, strong ({1} dBm). ", translate, span.toInt(), d.rssi) +
                        withYouNote(kind, d, translate = translate)
                presentAtStart && stillHere ->
                    Verdict.MOVED_WITH_YOU to
                        ReportText.format("GPS samples span {0} m along your path ({1} fixes). Already on the air when this window opened and still here. ", translate, span.toInt(), trail.size) +
                        withYouNote(kind, d, translate = translate)
                !presentAtStart && span >= MOVE_M && trail.size >= 3 ->
                    possibleTail(trail, span, opLen, kind, d, translate = translate)
                else ->
                    Verdict.STATIONARY to
                        ReportText.format("Heard along {0} m ({1} GPS stamps) but did not stay loud on you. Neighborhood arc / pass-by, not a tail.", translate, span.toInt(), trail.size)
            }
            FollowHit(d, label, verdict, detail, span, trail.size)
        }.sortedBy { it.verdict.ordinal }
    }

    private fun onBodyLine(kind: TrackerMatch.Kind, d: Sighting, stamps: Int, translate: (String) -> String = { it }): String {
        val loud = ReportText.format("Stayed loud with you the whole sit ({0} to {1} dBm, {2} GPS stamps). ", translate, d.rssiMax, d.rssiMin, stamps)
        return loud + withYouNote(kind, d, translate = translate)
    }

    /**
     * Catalog sentence for a live decode, when the signature wrote one.
     * A label with no sentence is named only. No fleet id is special.
     */
    private fun liveDecodeSentence(device: Sighting, translate: (String) -> String = { it }): String? {
        val chips = device.liveDecode
        if (chips.isEmpty()) return null
        val notes = chips.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (notes.isNotEmpty()) return notes.joinToString(" ")
        val labels = chips.reportLabels()
        if (labels.isEmpty()) return null
        return ReportText.format("Decoded: {0}.", translate, labels.joinToString(", "))
    }

    private fun withYouNote(kind: TrackerMatch.Kind, device: Sighting, translate: (String) -> String = { it }): String {
        val decoded = liveDecodeSentence(device, translate = translate)
        val base = when (kind) {
            TrackerMatch.Kind.FINDER ->
                translate("With you the whole sit — yours or planted before you started. Account for it.")
            TrackerMatch.Kind.BEACON ->
                translate("Location beacons do not typically move with you. Account for it (own test tag, badge, or a short overlap with a fixture).")
            TrackerMatch.Kind.WEARABLE ->
                translate("Typical of a watch or ring you or a companion are wearing. Not typically a planted tracker.")
        }
        return when {
            decoded != null -> "$base $decoded"
            kind == TrackerMatch.Kind.FINDER ->
                ReportText.format("{0} Find My / iPhone addresses rotate; this MAC is this session.", translate, base)
            else -> base
        }
    }

    /**
     * Extra gates on possible tail only. A neighborhood radio heard on a sidewalk
     * arc, or that faded as you walked, is stationary — not a follower.
     * Bag/car tags still cover most of the path and stay loud.
     */
    private fun possibleTail(
        trail: List<GpsSample>,
        span: Double,
        opLen: Double,
        kind: TrackerMatch.Kind,
        device: Sighting,
        translate: (String) -> String = { it },
    ): Pair<Verdict, String> {
        val trailLen = Geo.pathLengthM(trail)
        val peak = trail.maxOf { it.rssi }
        val last = trail.last().rssi
        val fade = peak - last
        val loudN = trail.count { it.rssi >= TRAIL_LOUD_DBM }
        val loudNeed = (trail.size * 2 + 2) / 3
        val coverNeed = opLen * COVER_FRAC
        val coverPct = if (opLen <= 0.0) 0 else ((trailLen / opLen) * 100.0).toInt()
        return when {
            fade >= FADE_DB ->
                Verdict.STATIONARY to
                    ReportText.format("Appeared after the sit started, but last GPS stamp was {0} dBm after a loudest of {1} dBm (−{2} dB). Looks like you walked away from a fixture, not a tail.", translate, last, peak, fade)
            loudN < loudNeed ->
                Verdict.STATIONARY to
                    ReportText.format("Appeared after the sit started and GPS span was {0} m, but only {1}/{2} stamps were loud (−75 dBm+). Looks like a pass-by, not a tail.", translate, span.toInt(), loudN, trail.size)
            trailLen < coverNeed ->
                Verdict.STATIONARY to
                    ReportText.format("Appeared after the sit started, but was only heard along {0} m of your {1} m path ({2}%). Neighborhood arc / pass-by, not a tail.", translate, trailLen.toInt(), opLen.toInt(), coverPct)
            else -> {
                val stats =
                    ReportText.format("Appeared after the sit started, then stayed loud with you across {0} m ", translate, span.toInt()) +
                        ReportText.format("({0} m of your {1} m path, {2}%; ", translate, trailLen.toInt(), opLen.toInt(), coverPct) +
                        ReportText.format("{0}/{1} GPS stamps ≥ −75 dBm). ", translate, loudN, trail.size)
                val note = when (kind) {
                    TrackerMatch.Kind.FINDER ->
                        translate("Treat as a possible tail until you visually account for it.")
                    TrackerMatch.Kind.BEACON ->
                        translate("Unusual for a retail/location beacon — they do not typically move with you. Account for it; not the same as a Find My tail.")
                    TrackerMatch.Kind.WEARABLE ->
                        translate("Typical of a watch that joined the sit (you put it on, or someone walking with you). Not typically a planted tracker.")
                }
                Verdict.FOLLOWING to stats + note
            }
        }.let { (verdict, text) ->
            val extra = liveDecodeSentence(device, translate = translate)
            verdict to if (extra == null) text else "$text $extra"
        }
    }

    private fun execSummary(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        named: List<Sighting>,
        hidden: List<Sighting>,
        randomized: Int,
        pathSpan: Double,
        pathLen: Double,
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        settings: AppSettings,
        places: DebriefPlaces,
        window: DebriefWindow,
        translate: (String) -> String = { it },
    ): String = buildString {
        val whenPhrase = if (window.sitName != null) {
            ReportText.format("In sit {0}", translate, window.sitName)
        } else {
            translate("In the last 15 minutes")
        }
        append(ReportText.format("{0} Fieldwatch heard {1} Wi-Fi access points and {2} BLE advertisers", translate, whenPhrase, wifi.size, ble.size))
        append(ReportText.format(" ({0} signature-matched, {1} hidden SSIDs, {2} randomized BLE). ", translate, named.size, hidden.size, randomized))
        if (settings.tagLocation && pathLen > 0) {
            append(ReportText.format("Overall distance traveled: {0} along the GPS path (straight-line span {1}). ", translate, fmtDist(pathLen, translate), fmtDist(pathSpan, translate)))
        }
        if (places.namesByCell.isNotEmpty()) {
            append(ReportText.format("Stops / area: {0}. ", translate, places.areaLine()))
        } else if (places.attempted && settings.tagLocation) {
            append("${places.note} ")
        }
        val wholeSit = ownLikely + withYou
        when {
            following.isNotEmpty() || wholeSit.isNotEmpty() -> {
                append(translate("TRACKING NOTE. "))
                if (wholeSit.isNotEmpty()) {
                    append(ReportText.format("{0} finder tag(s) with you the whole sit (your kit or planted before you started): ", translate, wholeSit.size))
                    append(wholeSit.joinToString { trackId(it) })
                    append(". ")
                }
                if (following.isNotEmpty()) {
                    append(ReportText.format("{0} possible tail(s) first heard after this sit started: ", translate, following.size))
                    append(following.joinToString { trackId(it) })
                    append(". ")
                }
                append(translate("Account for every MAC — Fieldwatch cannot tell yours from a plant. "))
            }
            !settings.tagLocation -> {
                append(translate("GPS tagging is off, so a following test was not performed. Enable “Tag detections with GPS” and walk to test. "))
            }
            pathSpan < MOVE_M -> {
                append(ReportText.format("GPS displacement was only {0} m — too short to test whether a tracker is following. Walk farther with tagging on. ", translate, pathSpan.toInt()))
            }
            else -> append(translate("No finder tag clearly stayed with the GPS path in this window. "))
        }
        if (beaconsWithYou.isNotEmpty()) {
            append(translate("Retail beacon(s) also stayed with the path (unusual — fixtures do not typically move with you): "))
            append(beaconsWithYou.joinToString { "${it.label} ${it.device.mac}" })
            append(". ")
        }
        if (wearablesWithYou.isNotEmpty()) {
            append(translate("Wearable(s) stayed with the path (usually your watch/ring or a companion): "))
            append(wearablesWithYou.joinToString { "${it.label} ${it.device.mac}" })
            append(".")
        }
    }

    private fun trackingSection(
        settings: AppSettings,
        path: List<GpsSample>,
        pathSpan: Double,
        pathLen: Double,
        following: List<FollowHit>,
        wholeSit: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        translate: (String) -> String = { it },
    ): String = buildString {
        if (!settings.tagLocation) {
            appendLine(translate("GPS tagging is OFF. Fieldwatch cannot test whether a radio moved with you."))
            appendLine(translate("Turn on Settings → Tag detections with GPS, walk or drive 50+ m, then run Debrief again."))
            return@buildString
        }
        appendLine(ReportText.format("Overall distance traveled: {0} along the GPS path ({1} samples). Straight-line span {2}.", translate, fmtDist(pathLen, translate), path.size, fmtDist(pathSpan, translate)))
        appendLine(translate("Co-travel is split by class: finder tags (AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee, loud pocket Apple), retail beacons (iBeacon, Minew, Estimote, Kontakt.io, Target Atrius basket), and wearables (Garmin, Fitbit, Oura)."))
        if (path.size < 2 || pathSpan < MOVE_M) {
            appendLine(translate("Insufficient movement to distinguish a radio that stayed with you from one you passed. Walk or drive farther and re-run."))
            return@buildString
        }
        if (following.isEmpty() && wholeSit.isEmpty() && beaconsWithYou.isEmpty() && wearablesWithYou.isEmpty()) {
            appendLine(translate("No finder tag, retail beacon, or wearable stayed with you. House tags and other radios you only passed are not listed."))
        } else {
            appendLine(translate("Callouts below are only radios that stayed with the path. Radios you passed (store fixtures, house tags) are omitted."))
        }
    }

    private fun observerNotesSection(
        devices: List<Sighting>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
        translate: (String) -> String = { it },
    ): String? {
        val hits = devices.mapNotNull { d ->
            val note = observerNotes[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            d to note
        }
        if (hits.isEmpty()) return null
        return buildString {
            appendLine(translate("Your captions on radios heard in this window. Same KIND+MAC as Named radios. Not catalog Notes."))
            hits.sortedWith(
                compareByDescending<Pair<Sighting, String>> { it.first.rssi }.thenBy { it.first.mac },
            ).forEach { (d, note) ->
                val kind = if (d.kind == RadioKind.WIFI) "WIFI" else "BLE"
                appendLine(ReportText.format("  · {0}  {1}  {2}  {3} dBm", translate, kind, d.reportName(customNames, translate), d.mac, d.rssi))
                appendLine("    $note")
            }
        }.trimEnd()
    }

    private fun trackerCallout(
        intro: String,
        rows: List<FollowHit>,
        customNames: Map<String, String> = emptyMap(),
        translate: (String) -> String = { it },
    ): String = buildString {
        appendLine(intro)
        appendLine()
        rows.forEach { h ->
            val d = h.device
            appendLine("  • ${h.label}")
            appendLine(ReportText.format("    {0}  {1}  RSSI {2} dBm (min {3} / max {4})", translate, d.reportName(customNames, translate), d.mac, d.rssi, d.rssiMin, d.rssiMax))
            appendLine("    ${h.detail}")
        }
    }.trimEnd()

    private fun whereYouWere(
        settings: AppSettings,
        path: List<GpsSample>,
        pathLen: Double,
        pathSpan: Double,
        devices: List<Sighting>,
        names: Map<String, String>,
        places: DebriefPlaces,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        translate: (String) -> String = { it },
    ): String = buildString {
        appendLine(translate("Phone GPS at hear-time, not the other radio’s location and not a camera pole. Stays are clusters within about 40 m; hops between them are transit. Coordinates are not repeated on every Wi-Fi/BLE line."))
        if (!settings.tagLocation) {
            appendLine(translate("GPS tagging is OFF. Turn on Settings → Tag detections with GPS to record where you were when radios were heard."))
            return@buildString
        }
        if (path.isEmpty()) {
            appendLine(translate("GPS tagging is on, but this window has no fixes yet."))
            return@buildString
        }
        appendLine(ReportText.format("Overall: {0} along-track, span {1}, {2} fixes.", translate, fmtDist(pathLen, translate), fmtDist(pathSpan, translate), path.size))
        if (places.attempted) {
            appendLine(places.note)
            appendLine(translate("Street names are approximate. Do not treat a street as the location of a matched camera or tag."))
        }
        val legs = Geo.legs(path, now = now)
        if (legs.isEmpty()) {
            appendLine(translate("No path legs."))
            return@buildString
        }
        val stopNames = legs.filter { it.stay }.mapNotNull { places.nameNear(it.lat, it.lon) }
        if (stopNames.isNotEmpty()) {
            appendLine(translate("Stops: ") + stopNames.joinToString(" → "))
        }
        var stayN = 0
        legs.forEachIndexed { i, leg ->
            if (leg.stay) {
                stayN++
                appendLine()
                appendLine(ReportText.format("{0}. Stay  {1}–{2} UTC  ({3})", translate, i + 1, clock(leg.startAt), clock(leg.endAt), fmtDur(leg.durationMs, translate = translate)))
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places, translate)}")
                val here = devices.filter { heardAt(it, leg) }
                val aps = here.count { it.kind == RadioKind.WIFI }
                val ble = here.count { it.kind == RadioKind.BLE }
                val sigs = here.flatMap { d -> d.fleetIds.map { names[it] ?: it } }.distinct()
                append(ReportText.format("   Heard here: {0} AP(s), {1} BLE", translate, aps, ble))
                if (sigs.isNotEmpty()) append("  ·  ${sigs.take(6).joinToString(", ")}")
                appendLine()
                here.filter { inventoryKeep(it, settings, bookmarkedKeys) }.sortedByDescending { it.rssi }.take(4).forEach { d ->
                    appendLine(ReportText.format("   · {0}  {1}  {2} dBm", translate, d.reportName(customNames, translate), d.mac, d.rssi))
                }
                if (here.isEmpty()) appendLine(translate("   · No GPS-stamped radios tied to this stay (tagging may have started after they were first heard)."))
            } else {
                appendLine()
                appendLine(
                    ReportText.format("{0}. Transit  {1}–{2} UTC  ", translate, i + 1, clock(leg.startAt), clock(leg.endAt)) +
                        ReportText.format("{0} along track", translate, fmtDist(leg.pathM, translate)),
                )
                appendLine("   ${placeAndGps(leg.lat, leg.lon, places, translate)}")
                appendLine("   → ${placeAndGps(leg.endLat, leg.endLon, places, translate)}")
            }
        }
        val stays = legs.count { it.stay }
        if (stays == 1 && pathSpan < MOVE_M) {
            appendLine()
            appendLine(translate("One stay — you did not move far enough in this window to split locations."))
        }
    }

    private fun heardAt(device: Sighting, leg: Geo.PathLeg): Boolean {
        val nearM = 60.0
        val trail = device.gpsTrail.filter { it.at >= leg.startAt && it.at <= leg.endAt }
        if (trail.isNotEmpty()) {
            return trail.any { Geo.meters(it.lat, it.lon, leg.lat, leg.lon) <= nearM }
        }
        val lat = device.latitude ?: return false
        val lon = device.longitude ?: return false
        if (device.lastSeen < leg.startAt || device.firstSeen > leg.endAt) return false
        return Geo.meters(lat, lon, leg.lat, leg.lon) <= nearM
    }

    private fun environment(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        randomized: Int,
        persistent: List<Sighting>,
        pathSpan: Double,
        pathLen: Double,
        translate: (String) -> String = { it },
    ): String {
        val ap = wifi.size
        val persistAp = persistent.count { it.kind == RadioKind.WIFI }
        val guess = when {
            pathSpan > 200 && ap in 1..25 -> translate("In motion (walk/vehicle) through mixed RF.")
            ap <= 4 && ble.size < 30 && persistAp >= 1 -> translate("Likely a dwelling or small office — few sitting APs, limited BLE.")
            ap >= 15 && randomized >= 40 -> translate("Dense public / retail / street: many APs and phone-like randomized BLE.")
            ap >= 8 && persistAp >= 4 -> translate("Likely a building with standing infrastructure APs plus patrons.")
            else -> translate("Mixed or under-sampled environment.")
        }
        return ReportText.format("{0}  ({1} APs, {2} BLE, {3} persistent APs, traveled {4}, span {5}.)", translate, guess, ap, ble.size, persistAp, fmtDist(pathLen, translate), fmtDist(pathSpan, translate))
    }

    private fun wifiLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
        translate: (String) -> String = { it },
    ): String = buildString {
        append(d.reportName(customNames, translate)).append("  ").append(d.mac)
        WifiWpsDecoder.identity(d.facts.vendorIes)?.let { wps ->
            if (!d.reportName(customNames, translate).contains(wps.model!!, ignoreCase = true)) {
                append("  WPS: ").append(wps.identityLabel())
            }
        }
        d.vendor?.let { append("  ").append(it) }
        val fields = AdvPayloadDecoder.decodeDevice(d, translate)
        if (fields.isNotEmpty()) append("  ").append(fields.joinToString("; ") { "${it.label}: ${it.value}" })
        append("  ").append(d.rssi).append(" dBm")
        if (d.channel != 0) append(translate("  ch ")).append(d.channel)
        if (d.hiddenSsid) append(translate("  hidden"))
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append(translate("  dwell ")).append(fmtDur(dwellMs(d, from, now), translate = translate))
    }

    private fun bleLine(
        d: Sighting,
        names: Map<String, String>,
        from: Long,
        now: Long,
        customNames: Map<String, String> = emptyMap(),
        translate: (String) -> String = { it },
    ): String = buildString {
        append(d.reportName(customNames, translate)).append("  ").append(d.mac)
        if (d.randomized) append(" " + translate(" RAND"))
        append("  ").append(d.rssi).append(" dBm")
        if (d.fleetIds.isNotEmpty()) append("  ").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        append(translate("  dwell ")).append(fmtDur(dwellMs(d, from, now), translate = translate))
    }

    /** Unmatched rotating BLE stays in counts/export; inventories omit it unless Extra attention, named, bookmark, or payload. */
    private fun inventoryKeep(
        d: Sighting,
        settings: AppSettings,
        bookmarkedKeys: Set<String>,
    ): Boolean {
        if (settings.debriefShowUnmatchedRandomBle) return true
        if (d.kind != RadioKind.BLE) return true
        if (!d.randomized) return true
        if (d.fleetIds.isNotEmpty()) return true
        if (d.payloadLat != null && d.payloadLon != null) return true
        if (d.key in bookmarkedKeys) return true
        return false
    }

    private fun anomalyLines(
        devices: List<Sighting>,
        customNames: Map<String, String> = emptyMap(),
        settings: AppSettings,
        bookmarkedKeys: Set<String>,
        translate: (String) -> String = { it },
    ): List<String> {
        val out = ArrayList<String>()
        val pairing = devices.filter { d ->
            d.facts.serviceData.any { it.uuid.contains("FE2C", true) && it.dataHex.length == 6 }
        }
        if (pairing.isNotEmpty()) {
            out += translate("Google Fast Pair in pairing mode: ") +
                pairing.joinToString { "${it.reportName(customNames, translate)} ${it.mac}" }
        }
        val loudUnknown = devices.filter {
            it.rssi >= -50 && it.fleetIds.isEmpty() && it.name.isBlank() &&
                inventoryKeep(it, settings, bookmarkedKeys)
        }
        if (loudUnknown.isNotEmpty()) {
            out += translate("Very strong unnamed radios (≥ −50 dBm): ") +
                loudUnknown.take(8).joinToString { ReportText.format("{0} {1} dBm", translate, it.mac, it.rssi) }
        }
        val rand = devices.count { it.kind == RadioKind.BLE && it.randomized }
        if (rand >= 20) {
            out += ReportText.format("High randomized BLE ({0}) — typical of phones, not a tracking finding.", translate, rand)
        }
        return out
    }

    private fun privacy(
        wifi: List<Sighting>,
        ble: List<Sighting>,
        randomized: Int,
        hidden: List<Sighting>,
        settings: AppSettings,
        places: DebriefPlaces,
        includeAircraft: Boolean,
        translate: (String) -> String = { it },
    ): String = buildString {
        append(ReportText.format("A passive observer with the same radios would see {0} named/hidden APs ", translate, wifi.size))
        append(ReportText.format("and {0} BLE advertisers ({1} randomized). ", translate, ble.size, randomized))
        if (hidden.isNotEmpty()) append(translate("Hidden SSIDs still beacon and identify the AP by BSSID. "))
        if (settings.tagLocation) append(translate("This debrief includes operator GPS samples used for distance and the following test. "))
        if (includeAircraft) {
            append(translate("This debrief includes advertised aircraft positions from radios that broadcast a latitude and longitude. "))
        }
        if (places.attempted && places.available) {
            append(translate("Street names came from the phone’s system geocoder while online. "))
        }
        append(translate("Do not share this file off-device without redaction."))
    }

    private fun actions(
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        settings: AppSettings,
        pathSpan: Double,
        translate: (String) -> String = { it },
    ): List<String> = buildList {
        if (following.isNotEmpty()) {
            add(ReportText.format("Possible tail (appeared after this sit started): {0}. Pause Live, open detail, note RSSI while you walk a dog-leg. Do not disable someone else’s tag.", translate, following.joinToString { trackId(it) }))
        }
        if (ownLikely.isNotEmpty() || withYou.isNotEmpty()) {
            add(
                ReportText.format("Possible trackers with you: {0}. ", translate, (ownLikely + withYou).joinToString { trackId(it) }) +
                    translate("Could be yours or planted in the car/bag/on you before you started. Account for each MAC — do not dismiss as yours."),
            )
        }
        if (beaconsWithYou.isNotEmpty()) {
            add(
                translate("Retail beacons with you (unusual — fixtures do not typically move with you): ") +
                    beaconsWithYou.joinToString { it.label + " " + it.device.mac } +
                    translate(". Account for a test tag or badge before treating it as a follower."),
            )
        }
        if (wearablesWithYou.isNotEmpty()) {
            add(
                translate("Wearables with you (usually own kit or a companion): ") +
                    wearablesWithYou.joinToString { it.label + " " + it.device.mac } +
                    ".",
            )
        }
        if (!settings.tagLocation) add(translate("Enable Tag detections with GPS and walk 50+ m, then run Debrief again for a following test."))
        else if (pathSpan < MOVE_M) add(translate("Walk farther (50+ m) with GPS tagging on, then re-run Debrief."))
        add(translate("Use Live → Pause to inspect a busy list. Watch tracker signatures if this sit was noisy."))
        add(translate("Station-side Wi-Fi (probes/clients) still needs a dedicated sniffer — Fieldwatch cannot see them."))
    }

    private fun takeaway(
        following: List<FollowHit>,
        withYou: List<FollowHit>,
        ownLikely: List<FollowHit>,
        beaconsWithYou: List<FollowHit>,
        wearablesWithYou: List<FollowHit>,
        pathSpan: Double,
        settings: AppSettings,
        named: List<Sighting>,
        translate: (String) -> String = { it },
    ): String {
        val extra = buildString {
            if (beaconsWithYou.isNotEmpty()) {
                append(translate(" Retail beacon(s) also with the path (unusual): "))
                append(beaconsWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
            if (wearablesWithYou.isNotEmpty()) {
                append(translate(" Wearable(s) with the path (usually own kit): "))
                append(wearablesWithYou.joinToString { it.label + " (" + it.device.mac + ")" })
                append(".")
            }
        }
        val core = when {
            following.isNotEmpty() && (ownLikely.isNotEmpty() || withYou.isNotEmpty()) ->
                ReportText.format("Possible tail (appeared after sit started): {0}. ", translate, following.joinToString { trackId(it) }) +
                    ReportText.format("Also finder tags with you (yours or planted before): {0}. Account for every MAC.", translate, (ownLikely + withYou).joinToString { trackId(it) })
            following.isNotEmpty() ->
                ReportText.format("Possible tail (appeared after this sit started): {0}. Account for it on the person/vehicle.", translate, following.joinToString { trackId(it) })
            !settings.tagLocation ->
                translate("Turn on GPS tagging and walk before you can test whether a tracker is following you.")
            pathSpan < MOVE_M ->
                ReportText.format("Not enough GPS movement ({0} m) to test following; walk and re-run Debrief.", translate, pathSpan.toInt())
            ownLikely.isNotEmpty() || withYou.isNotEmpty() ->
                ReportText.format("Finder tags with you (yours or planted before you started): {0}. No new arrival this window. Account for each MAC — do not dismiss as yours.", translate, (ownLikely + withYou).joinToString { trackId(it) })
            beaconsWithYou.isNotEmpty() || wearablesWithYou.isNotEmpty() ->
                translate("No finder tag stayed with the path.")
            named.isEmpty() ->
                translate("No signature hits and no GPS co-travel of trackers in this 15-minute window.")
            else ->
                translate("No finder tag, retail beacon, or wearable clearly stayed with your GPS path in this window.")
        }
        return (core + extra).trim()
    }

    /** Label and MAC, plus the live-decode name when the signature asked for one. */
    private fun trackId(hit: FollowHit): String {
        val labels = hit.device.liveDecode.reportLabels()
        val id = "${hit.label} ${hit.device.mac}"
        return if (labels.isEmpty()) id else "$id (${labels.joinToString(", ")})"
    }

    private fun craftSentence(pictures: List<AircraftTrail.Picture>, translate: (String) -> String = { it }): String {
        if (pictures.isEmpty()) return ""
        val bits = pictures.take(3).joinToString { pic ->
            if (pic.status.isBlank()) pic.title else "${pic.title} (${pic.status})"
        }
        val more = if (pictures.size > 3) ReportText.format(" and {0} more", translate, pictures.size - 3) else ""
        return ReportText.format(" Advertised position: {0}{1}.", translate, bits, more)
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

    private fun absDelta(a: Long, b: Long) = kotlin.math.abs(a - b)

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun clock(ms: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun fmtDist(m: Double, translate: (String) -> String = { it }): String =
        if (m >= 1000.0) ReportText.format("{0} km", translate, String.format(Locale.US, "%.2f", m / 1000.0))
        else ReportText.format("{0} m", translate, m.toInt())

    private fun fmtCoord(s: GpsSample): String =
        String.format(Locale.US, "%.5f, %.5f", s.lat, s.lon)

    private fun placeAndGps(lat: Double, lon: Double, places: DebriefPlaces, translate: (String) -> String = { it }): String {
        val gps = fmtCoord(GpsSample(0L, lat, lon))
        val name = places.nameNear(lat, lon)
        return if (!name.isNullOrBlank()) {
            ReportText.format("{0}  ({1}, operator phone)", translate, name, gps)
        } else if (places.attempted) {
            ReportText.format("{0}  (operator phone; no street name this export)", translate, gps)
        } else {
            ReportText.format("{0}  (operator phone)", translate, gps)
        }
    }

    private fun fmtDur(ms: Long, translate: (String) -> String = { it }): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) ReportText.format("{0}h{1}m", translate, m / 60, m % 60) else if (m > 0) ReportText.format("{0}m{1}s", translate, m, r) else ReportText.format("{0}s", translate, r)
    }
}
