package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryV98Test {
    @Test fun catalogVersionIs98() {
        assertEquals(98, ConfigStore.CATALOG_VERSION)
    }

    @Test fun v98ReAddsTheMissingBuiltInMercuryRowOnly() {
        val stock = DefaultCatalog.fleets()
        val withoutMercury = stock.filterNot { it.id == "fleet-mercury-wifi" }
        val custom = Fleet(id = "operator", name = "Operator row", builtIn = false)
        val initial = withoutMercury + custom
        val migrated = ConfigStore.appendCatalogV98(initial)
        val byId = migrated.associateBy { it.id }
        val mercury = stock.single { it.id == "fleet-mercury-wifi" }
        assertEquals(mercury, byId.getValue("fleet-mercury-wifi"))
        assertEquals(initial.size + 1, migrated.size)
        assertTrue(migrated.any { it.id == custom.id })
        // Idempotent: a config that already has the row is untouched.
        assertEquals(migrated, ConfigStore.appendCatalogV98(migrated))
    }

    @Test fun v98DoesNotTouchConfigsThatAlreadyHaveTheRow() {
        val stock = DefaultCatalog.fleets()
        val edited = stock.first { it.id == "fleet-mercury-wifi" }.copy(name = "My Mercury", notes = "kept")
        val initial = stock.map { if (it.id == "fleet-mercury-wifi") edited else it }
        val migrated = ConfigStore.appendCatalogV98(initial)
        assertEquals(initial, migrated)
        assertFalse(migrated.any { it.id == "fleet-mercury-wifi" && it.name != "My Mercury" })
    }
}
