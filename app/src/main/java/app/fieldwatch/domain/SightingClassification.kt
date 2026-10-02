package app.fieldwatch.domain

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
