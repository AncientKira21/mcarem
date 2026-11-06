# MCA books on lecterns

All 13 MCA written-book items, including letters and the civil registry, are added to Minecraft's lectern-books tag without replacing vanilla entries. They also carry standard written-book content. Placing a book on an empty lectern refreshes those pages on the server before the vanilla lectern stores it.

Cinematic Interactions already opens its lectern scene through the vanilla lectern menu and reads `WRITTEN_BOOK_CONTENT` through `BookViewScreen.BookAccess`. MCA now supplies the data that path expects; no Cinematic Interactions classes, duplicate items, optional dependencies or separate loader implementations are needed. Normal page navigation, comparators and taking the original book back use the existing lectern behavior. Held-book use retains MCA's existing reader.

Text/title pages and paginated lists export their components without mutating MCA GUI state. Letters use their stored MCA page payload. The civil registry takes a snapshot of up to 100 current registry records for the player placing it, so that snapshot is readable from the lectern. Supporter lists refresh at insertion; dedicated servers read the bundled credit lists because they do not run client resource listeners. Default item registration does not invoke dynamic resource generators. The cinematic reader uses its normal book presentation; MCA-specific page backgrounds and scribble artwork remain features of the MCA reader.

Checks: `python3 source/tools/validate_lectern_books.py` exercises 24 production export/refresh and localization cases and verifies all 13 book IDs in the additive lectern tag. Builds do not launch Minecraft. Actual game/mod interaction remains unverified in-game.

BookText now supplies bundled English fallbacks for book components and resolves native title/author metadata before client languages exist. Native paper exports remove legacy white ink codes while leaving the MCA reader’s original page styles intact. Cinematic Interactions owns handheld reading; its registry list pagination also applies to lecterns.
