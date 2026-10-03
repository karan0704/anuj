package com.karan.anuj.core.domain.history

/**
 * Compares the before and after picture of one row and returns a
 * [RecordChange] for every field whose value is different.
 *
 * A field present on only one side counts as changed from, or to, null.
 * Fields are reported in name order so the output is the same on every run.
 */
fun diffFields(
    table: String,
    rowId: String,
    before: Map<String, String?>,
    after: Map<String, String?>,
    changedAt: Long,
): List<RecordChange> =
    (before.keys + after.keys)
        .sorted()
        .filter { before[it] != after[it] }
        .map { field ->
            RecordChange(
                table = table,
                rowId = rowId,
                field = field,
                oldValue = before[field],
                newValue = after[field],
                changedAt = changedAt,
            )
        }
