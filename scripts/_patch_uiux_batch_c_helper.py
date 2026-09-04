from pathlib import Path

path = Path('scripts/_uiux_batch_c.py')
text = path.read_text()
start_marker = "old = '''                    ActivityDetailContent("
end_marker = "\nold = ''') {\n    LazyColumn("
start = text.index(start_marker)
end = text.index(end_marker, start)
replacement = r'''wide_scope = t.index('                    val selected = state.selectedItem ?: state.visibleItems.firstOrNull()')
wide_start = t.index('                    ActivityDetailContent(', wide_scope)
wide_end_marker = ''' + '"""                        modifier = Modifier.weight(0.85f),\n                    )"""' + r'''
wide_end = t.index(wide_end_marker, wide_start) + len(wide_end_marker)
old_wide = t[wide_start:wide_end]
assert 'onAction(ActivityAction.SaveEdit(selected.id, note, category))' in old_wide, 'wide detail semantic guard failed'
new_wide = ''' + "'''" + r'''                    ActivityDetailContent(
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
