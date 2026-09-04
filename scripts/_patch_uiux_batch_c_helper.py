from pathlib import Path

path = Path('scripts/_uiux_batch_c.py')
text = path.read_text()

# Make the wide-layout replacement structural and use named arguments for the richer action.
start_marker = "old = '''                    ActivityDetailContent("
end_marker = "\nold = ''') {\n    LazyColumn("
start = text.index(start_marker)
end = text.index(end_marker, start)
replacement = r'''wide_scope = t.index('                    val selected = state.selectedItem ?: state.visibleItems.firstOrNull()')
wide_start = t.index('ActivityDetailContent(', wide_scope)
modifier_pos = t.index('modifier = Modifier.weight(0.85f),', wide_start)
modifier_line_end = t.index('\n', modifier_pos)
wide_end = t.index(')', modifier_line_end) + 1
old_wide = t[wide_start:wide_end]
assert 'onAction(ActivityAction.SaveEdit(selected.id, note, category))' in old_wide, 'wide detail semantic guard failed'
assert 'onDelete = { onAction(ActivityAction.Delete(selected.id)) }' in old_wide, 'wide delete semantic guard failed'
new_wide = ''' + "'''" + r'''ActivityDetailContent(
                            item = selected,
                            categoryOptions = state.categoryOptionsFor(selected),
                            onSave = { date, note, category, subcategory ->
                                onAction(
                                    ActivityAction.SaveEdit(
                                        id = selected.id,
                                        note = note,
                                        category = category,
                                        date = date,
                                        subcategory = subcategory,
                                    ),
                                )
                            },
                            onDelete = { onAction(ActivityAction.Delete(selected.id)) },
                            modifier = Modifier.weight(0.85f),
                        )''' + "'''" + r'''
t = t[:wide_start] + new_wide + t[wide_end:]
'''
text = text[:start] + replacement + text[end:]

# Keep pre-existing reducer/tests/source callers valid: the old action contract remains the first
# three parameters; date/subcategory are optional enrichments from the new editor.
old = '''new = ''' + "'''" + r'''    data class SaveEdit(
        val id: String,
        val date: String,
        val note: String,
        val category: String,
        val subcategory: String,
    ) : ActivityAction''' + "'''"
new = '''new = ''' + "'''" + r'''    data class SaveEdit(
        val id: String,
        val note: String,
        val category: String,
        val date: String? = null,
        val subcategory: String? = null,
    ) : ActivityAction''' + "'''"
assert text.count(old) == 1, 'SaveEdit generation patch guard failed'
text = text.replace(old, new)

old = '''new = ''' + "'''" + r'''            if (item.id == action.id) {
                item.copy(
                    rawDate = action.date,
                    dateLabel = action.date,
                    subtitle = action.note,
                    category = action.category.takeIf(String::isNotBlank),
                    subcategory = action.subcategory.takeIf(String::isNotBlank),
                )
            } else {''' + "'''"
new = '''new = ''' + "'''" + r'''            if (item.id == action.id) {
                item.copy(
                    rawDate = action.date ?: item.rawDate,
                    dateLabel = action.date ?: item.dateLabel,
                    subtitle = action.note,
                    category = action.category.takeIf(String::isNotBlank),
                    subcategory = if (action.subcategory != null) {
                        action.subcategory.takeIf(String::isNotBlank)
                    } else {
                        item.subcategory
                    },
                )
            } else {''' + "'''"
assert text.count(old) == 1, 'reducer compatibility generation patch guard failed'
text = text.replace(old, new)

old = 'onActivityAction(ActivityAction.SaveEdit(route.eventId, date, note, category, subcategory))'
new = '''onActivityAction(
                                ActivityAction.SaveEdit(
                                    id = route.eventId,
                                    note = note,
                                    category = category,
                                    date = date,
                                    subcategory = subcategory,
                                ),
                            )'''
assert text.count(old) == 1, 'route action generation patch guard failed'
text = text.replace(old, new)

path.write_text(text)
