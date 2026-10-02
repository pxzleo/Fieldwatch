package app.fieldwatch.data

import app.fieldwatch.domain.*
import org.junit.Assert.*
import org.junit.Test

class CatalogV89MigrationTest {
    @Test fun upgrade88AddsOnlyMissingDomesticIdsAndIsIdempotent() {
        assertEquals(94, ConfigStore.CATALOG_VERSION)
        val old = DefaultCatalog.fleets().filterNot { it.id in DefaultCatalog.domesticFamilies().map { f -> f.id } }
        val migrated = ConfigStore.appendCatalogV89(old)
        assertEquals(old, migrated.take(old.size))
        assertEquals(DefaultCatalog.domesticFamilies().map { it.id }.toSet(), migrated.drop(old.size).map { it.id }.toSet())
        assertEquals(migrated, ConfigStore.appendCatalogV89(migrated))
    }

    @Test fun existingStockAndCustomRowsKeepAllOperatorChanges() {
        val edited = DefaultCatalog.domesticFamilies().first().copy(name = "My router", notes = "My note", enabled = false,
            rules = listOf(MatchRule(RuleKind.MAC_PREFIX, text = "00:11:22:00:00:01", enabled = false)),
            colorIndex = 5, matchAny = false, attentionNote = "My alert")
        val custom = DefaultCatalog.domesticFamilies()[1].copy(builtIn = false, name = "My signature", enabled = false)
        val unrelated = Fleet(id = "operator", name = "Operator row", builtIn = false)
        val initial = listOf(edited, custom, unrelated)
        val catalog = DefaultCatalog.fleets().associateBy { it.id }
        val patched = ConfigStore.patchBuiltInRules(initial, catalog)
        assertEquals(initial, patched)
        val migrated = ConfigStore.appendCatalogV89(patched)
        assertEquals(initial, migrated.take(3))
        assertEquals(1, migrated.count { it.id == edited.id })
        assertEquals(1, migrated.count { it.id == custom.id })
        assertEquals(migrated, ConfigStore.patchBuiltInRules(migrated, catalog))
        assertEquals(migrated, ConfigStore.appendCatalogV89(ConfigStore.patchBuiltInRules(migrated, catalog)))
    }

    @Test fun everyExistingDomesticRowSurvivesInitialPatchWithEditedAndRules() {
        val catalog = DefaultCatalog.fleets().associateBy { it.id }
        val edited = DefaultCatalog.domesticFamilies().map { it.copy(matchAny = false, enabled = false,
            name = "Operator ${it.id}", notes = "Operator notes", attentionNote = "Operator alert",
            rules = listOf(MatchRule(RuleKind.MAC_PREFIX, text = "00:11:22:00:00:01"))) }
        val upgraded = ConfigStore.appendCatalogV89(ConfigStore.patchBuiltInRules(edited, catalog))
        assertEquals(edited, upgraded)
        assertEquals(edited, ConfigStore.patchBuiltInRules(upgraded, catalog))
    }

    @Test fun historicalStockStillGetsMissingRulesWhileCustomRowsRemainUntouched() {
        val stock = DefaultCatalog.fleets().first { it.id == "fleet-seos" }
        val old = stock.copy(name = "Seos", rules = emptyList(), enabled = false)
        val custom = old.copy(id = "custom", builtIn = false)
        val catalog = mapOf(stock.id to stock, custom.id to stock.copy(id = custom.id))
        val result = ConfigStore.patchBuiltInRules(listOf(old, custom), catalog)
        assertEquals(old.copy(name = stock.name, rules = stock.rules), result.first())
        assertEquals(custom, result.last())
    }
}
