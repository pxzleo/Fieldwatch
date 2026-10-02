package app.fieldwatch

import app.fieldwatch.domain.DecodeField
import app.fieldwatch.domain.DecodeType
import app.fieldwatch.domain.DecodedFieldValue
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.resolvedLength
import org.junit.Assert.assertEquals
import org.junit.Test

class UiCatalogTextTest {
    private val fleets = DefaultCatalog.fleets()
    private val mark: (String) -> String = { "translated:$it" }

    @Test
    fun unchangedStockNotesAndAttentionAreTranslated() {
        val fleet = fleets.first { it.attentionNote.isNotBlank() }
        assertEquals("translated:${fleet.notes}", UiCatalogText.forFleet(fleet, fleet.notes, mark))
        assertEquals("translated:${fleet.attentionNote}", UiCatalogText.forFleet(fleet, fleet.attentionNote, mark))
    }

    @Test
    fun customSignaturesAndEditedNotesKeepOriginalText() {
        val stock = fleets.first()
        val custom = stock.copy(builtIn = false)
        assertEquals(custom.notes, UiCatalogText.forFleet(custom, custom.notes, mark))
        val edited = stock.copy(notes = "Operator note", attentionNote = "Operator caution")
        assertEquals(edited.notes, UiCatalogText.forFleet(edited, edited.notes, mark))
        assertEquals(edited.attentionNote, UiCatalogText.forFleet(edited, edited.attentionNote, mark))
        assertEquals(stock.notes, UiCatalogText.forFleet(null, stock.notes, mark))
    }

    @Test
    fun editedNotesMatchingAStockLabelOrEnumAreStillOperatorText() {
        val stock = fleets.first { it.name == "GoPro" }
        val edited = stock.copy(notes = "awake", attentionNote = "Battery", name = "Processor")
        assertEquals("awake", UiCatalogText.forFleet(edited, edited.notes, mark))
        assertEquals("Battery", UiCatalogText.forFleet(edited, edited.attentionNote, mark))
        assertEquals("Processor", UiCatalogText.forFleet(edited, edited.name, mark))
    }

    @Test
    fun stockDecodeLabelsAndEnumNotesAreTranslated() {
        val fleet = fleets.first { f -> f.decode?.fields?.any { !it.enumNotes.isNullOrEmpty() } == true }
        val field = fleet.decode!!.fields.first { !it.enumNotes.isNullOrEmpty() }
        val note = field.enumNotes!!.values.first()
        assertEquals("translated:${field.label}", UiCatalogText.forFleet(fleet, field.label, mark))
        assertEquals("translated:$note", UiCatalogText.forFleet(fleet, note, mark))
    }

    @Test
    fun decodedUtf8RadioTextIsNeverTranslatedAsACatalogLabel() {
        val fleet = fleets.first { f -> f.decode?.fields?.any { it.type == DecodeType.UTF8 } == true }
        val field = fleet.decode!!.fields.first { it.type == DecodeType.UTF8 }
        // Temperature is a mapped stock label, but this value came from the device's own bytes.
        val decoded = row(fleet, field, "Temperature")
        assertEquals("Temperature", UiCatalogText.decoded(fleet, decoded, mark))
    }

    @Test
    fun stockEnumDisplayIsTranslated() {
        val fleet = fleets.first { it.name == "GoPro" }
        val field = fleet.decode!!.fields.first { it.id == "awake" }
        assertEquals("translated:awake", UiCatalogText.decoded(fleet, row(fleet, field, "awake"), mark))
        val custom = fleet.copy(builtIn = false)
        assertEquals("awake", UiCatalogText.decoded(custom, row(custom, field, "awake"), mark))
    }

    @Test
    fun rawNumbersUnitsAndIdentifiersRemainUnchanged() {
        val fleet = fleets.first { it.name == "Ruuvi" }
        for ((id, display) in listOf("temperature" to "21.5 °C", "format" to "5", "mac" to "AA:BB:CC:DD:EE:FF")) {
            val field = fleet.decode!!.fields.first { it.id == id }
            assertEquals(display, UiCatalogText.decoded(fleet, row(fleet, field, display), mark))
        }
        val tpms = fleets.first { it.name == "Aftermarket TPMS" }
        val sensor = tpms.decode!!.fields.first { it.id == "sensor_id" }
        assertEquals("54 65 6D 70 31", UiCatalogText.decoded(tpms, row(tpms, sensor, "54 65 6D 70 31"), mark))
        val wheel = tpms.decode!!.fields.first { it.id == "wheel" }
        assertEquals("1", UiCatalogText.decoded(tpms, row(tpms, wheel, "1")))
        val gopro = fleets.first { it.name == "GoPro" }
        val model = gopro.decode!!.fields.first { it.id == "model" }
        assertEquals("HERO9 Black", UiCatalogText.decoded(gopro, row(gopro, model, "HERO9 Black")))
    }

    @Test
    fun modifiedDecodeFieldsDoNotReceiveStockTranslations() {
        val stock = fleets.first { it.name == "GoPro" }
        val field = stock.decode!!.fields.first { it.id == "awake" }
        val changed = field.copy(offset = field.offset + 1)
        val edited = stock.copy(decode = stock.decode!!.copy(fields = listOf(changed)))
        assertEquals(changed.label, UiCatalogText.forFleet(edited, changed.label, mark))
        assertEquals("awake", UiCatalogText.decoded(edited, row(edited, changed, "awake"), mark))
    }

    private fun row(fleet: Fleet, field: DecodeField, display: String) = DecodedFieldValue(
        fleetId = fleet.id,
        fleetName = fleet.name,
        id = field.id,
        label = field.label,
        display = display,
        offset = field.offset,
        length = field.resolvedLength(),
    )
}
