# Native mixed-card vertical stack gesture and restoration contract

The owner's Android mixed debit/credit/virtual card stack is navigated by vertical swipe, **not arrows**. Upward swipe selects next canonical card; downward swipe returns to previous, wrapping through 3+ cards. TalkBack custom previous/next actions and keyboard Up/Down use matching semantics. The exact 150ms depart/320ms tuck animation and existing visual design remain unchanged.

Selected card/physical stack order is saved in Android's Compose saveable registry across an Activity recreation. Rehydration reconciles with the current canonical card IDs: discarded/archived cards are removed; newly added cards appear after surviving saved order. Stable IDs preserve the correct inline credit debt/activity workspace and avoid unintended switching back to first card. No financial state mutation or secret storage.

Unit tests cover 3-card forward/backward and wrap, duplicate/stale source IDs; hosted Compose tests cover real vertical gestures and restoration/removed card selection. Physical owner S24 acceptance #149 is separate.
