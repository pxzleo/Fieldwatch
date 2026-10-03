package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryV95Test {
    private val families = DefaultCatalog.discoveryFamiliesV95()

    @Test fun catalogVersionIsCurrent() {
        assertEquals(101, ConfigStore.CATALOG_VERSION)
        assertTrue(families.isNotEmpty())
        families.forEach { fleet -> assertTrue(fleet.builtIn) }
    }

    @Test fun migrationAddsOnlyV95FamiliesAndIsIdempotent() {
        val custom = Fleet(id = "operator", name = "Operator row", builtIn = false)
        val result = ConfigStore.appendCatalogV95(listOf(custom))
        assertEquals(1, result.count { it.id == custom.id })
        assertEquals(families.map { it.id }.toSet(), result.drop(1).map { it.id }.toSet())
        assertEquals(result, ConfigStore.appendCatalogV95(result))
    }

    @Test fun aimaRowGainsManufacturerRulesOnly() {
        val stock = DefaultCatalog.domesticFamilies().single { it.id == "fleet-aima-vehicle" }
        val stripped = stock.copy(rules = stock.rules.filterNot {
            it.kind == RuleKind.MANUFACTURER_ID || it.kind == RuleKind.MANUFACTURER_DATA
        })
        val migrated = ConfigStore.appendCatalogV95(listOf(stripped)).first()
        assertEquals(stock.rules.toSet(), migrated.rules.toSet())
        assertEquals(stock.notes, migrated.notes)
        assertEquals(migrated, ConfigStore.appendCatalogV95(listOf(migrated)).first())
    }

    @Test fun nonDomesticRowsAreExtendedByPatchBuiltInRules() {
        val catalog = DefaultCatalog.fleets().associateBy { it.id }
        val byd = catalog.getValue("fleet-byd").copy(
            rules = listOf(MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0C34)),
        )
        val patched = ConfigStore.patchBuiltInRules(listOf(byd), catalog).single()
        assertEquals(catalog.getValue("fleet-byd").rules.toSet(), patched.rules.toSet())
        val niu = catalog.getValue("fleet-niu-link").copy(
            rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "NIU Link *", radio = RadioKind.BLE)),
        )
        val patchedNiu = ConfigStore.patchBuiltInRules(listOf(niu), catalog).single()
        assertEquals(catalog.getValue("fleet-niu-link").rules.toSet(), patchedNiu.rules.toSet())
    }

    @Test fun aimaRowIsSkippedByPatchBuiltInRules() {
        val catalog = DefaultCatalog.fleets().associateBy { it.id }
        val aima = DefaultCatalog.domesticFamilies().single { it.id == "fleet-aima-vehicle" }
            .copy(rules = emptyList())
        val untouched = ConfigStore.patchBuiltInRules(listOf(aima), catalog).single()
        assertTrue(untouched.rules.isEmpty())
    }
}
