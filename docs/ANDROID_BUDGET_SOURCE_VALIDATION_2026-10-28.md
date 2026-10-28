# Android budget money validation — 28 October 2026

The owner-selected mobile Plan supports a monthly budget and category/subcategory limits, not new finance transactions. Previously the three editor paths used inconsistent Double checks: an overall limit could accept Infinity, and the budget preview could render invalid NaN/Infinity source values.

A shared parser now accepts only positive, exact two-decimal-or-fewer euro values (comma or dot), performs integer-cent bounds checks before returning a display/use value and rejects exponent notation, nonfinite inputs, negative/zero limits and sub-cent values. Overall budget save and reducer, category editor/reducer and subcategory editor use the same guard. Imported nonfinite overall spending or overflow cannot produce a misleading budget progress bar. Nonfinite projected commitments are not shown. Existing data and Web finance calculation/storage are unchanged.

Unit tests assert parsing, safe equivalent drafts, unknown source progress and reducer validation. Hosted Compose test confirms a malformed imported overall budget cannot render NaN/infinite progress or dispatch budget mutations. Release and physical owner S24 signoff remain separate.
