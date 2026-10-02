package app.fieldwatch.ui

import app.fieldwatch.R
import app.fieldwatch.UiText
import app.fieldwatch.domain.SettingsImportResult
import app.fieldwatch.domain.SignatureImportResult

internal fun SignatureImportResult.uiSummary(): String {
    error?.let { return it }
    if (added == 0 && merged == 0) return if (skipped == 0) {
        UiText.text(R.string.import_nothing)
    } else UiText.text(R.string.import_already_present, skipped)
    return buildList {
        if (added > 0) add(if (renamed > 0) UiText.text(R.string.import_added_renamed, added, renamed)
            else UiText.text(R.string.import_added, added))
        if (merged > 0) add(UiText.text(R.string.import_merged, merged))
        if (skipped > 0) add(UiText.text(R.string.import_skipped, skipped))
    }.joinToString(" · ")
}

internal fun SettingsImportResult.uiSummary(): String = error
    ?: UiText.text(R.string.import_settings_summary, presets, namedRadios, signatureWatches)
