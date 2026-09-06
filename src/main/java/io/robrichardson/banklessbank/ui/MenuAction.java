package io.robrichardson.banklessbank.ui;

/** Context-menu action ids. */
public enum MenuAction
{
	RELEASE_PLACEHOLDER, RELEASE_ALL_IN_TAB, RELEASE_ALL, NEW_TAB_FROM_ITEM,
	SET_TAB_ICON, CLEAR_TAB_ICON, MOVE_TO_MAIN, DELETE_TAB, COMPACT_TAB, RENAME_TAB,
	IGNORE_PLACEHOLDER, UNIGNORE_PLACEHOLDER, CLOSE_VIEW, CANCEL,
	/** arg = item id, arg2 = owner tab index -> opens the step-2 "copy to tab" menu. */
	COPY_TO_TAB_MENU,
	/** arg = target tab index, arg2 = item id. */
	COPY_TO_TAB,
	/** arg = tab index, arg2 = slot index. */
	REMOVE_COPY,
	/** arg = item id, arg2 = the page to show -> reopens the step-2 menu at that page. */
	COPY_TO_TAB_PAGE
}
