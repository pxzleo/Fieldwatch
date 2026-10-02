package app.fieldwatch.domain

/** Translate an author template before inserting unmodified names, notes and radio data. */
object ReportText {
    private val slots = Regex("\\{(\\d+)\\}")

    fun format(source: String, translate: (String) -> String, vararg values: Any?): String =
        slots.replace(translate(source)) { match ->
            val index = match.groupValues[1].toInt()
            require(index < values.size) { "Report template refers to missing argument $index: $source" }
            values[index].toString()
        }
}
