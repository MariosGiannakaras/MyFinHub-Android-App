from pathlib import Path

path = Path('scripts/_uiux_batch_c.py')
text = path.read_text()
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
                                onAction(ActivityAction.SaveEdit(selected.id, date, note, category, subcategory))
                            },
                            onDelete = { onAction(ActivityAction.Delete(selected.id)) },
                            modifier = Modifier.weight(0.85f),
                        )''' + "'''" + r'''
t = t[:wide_start] + new_wide + t[wide_end:]
'''
path.write_text(text[:start] + replacement + text[end:])
