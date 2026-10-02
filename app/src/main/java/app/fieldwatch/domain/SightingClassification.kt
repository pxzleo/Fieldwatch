package app.fieldwatch.domain

/** Live rows are already classified by DeviceStore; only a held, absent selection needs matching. */
fun Collection<Sighting>.resolveLiveSelection(
    key: String?,
    held: Sighting?,
    classify: (Collection<Sighting>) -> List<Sighting>,
): Sighting? {
    if (key == null) return null
    firstOrNull { it.key == key }?.let { return it }
    val snapshot = held?.takeIf { it.key == key } ?: return null
    val absent = if (snapshot.gone) snapshot else snapshot.copy(gone = true)
    // Keep the live peer context for custom cluster rules.
    return classify(this + absent).firstOrNull { it.key == key }
}

/** Reclassify an in-memory view; saved sightings and observation files stay unchanged. */
fun Collection<Sighting>.reclassify(
    fleets: List<Fleet>,
    engine: SignatureEngine = SignatureEngine(),
    policy: DetectionPolicy = DetectionPolicy(),
): List<Sighting> {
    val now = maxOfOrNull { it.lastSeen } ?: return emptyList()
    val matches = engine.match(this, fleets, now, policy)
    return map { device ->
        device.copy(fleetIds = matches[device.key].orEmpty()).withLiveDecode(fleets)
    }
}
