package io.github.swat.backend.gtk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * FFM bindings to GTK4, GLib, and GObject. Loads {@code libgtk-4.so.1} (and
 * its dependencies indirectly) and caches downcall handles for the small
 * surface used by the backend.
 *
 * <p>{@link #available()} can be called from any thread to probe whether GTK4
 * is installed without throwing on unsupported hosts.
 */
final class Gtk {
    private Gtk() {}

    static final ValueLayout PTR = ValueLayout.ADDRESS;
    static final ValueLayout INT = ValueLayout.JAVA_INT;
    static final ValueLayout LONG = ValueLayout.JAVA_LONG;
    static final ValueLayout BOOL = ValueLayout.JAVA_INT; // gboolean is gint

    static final Linker LINKER = Linker.nativeLinker();
    static final Arena GLOBAL = Arena.ofShared();

    private static final SymbolLookup LIB;
    private static final boolean AVAILABLE;

    static {
        SymbolLookup lib;
        boolean ok;
        try {
            lib = SymbolLookup.libraryLookup("libgtk-4.so.1", GLOBAL);
            ok = lib.find("gtk_init").isPresent();
        } catch (Throwable t) {
            lib = name -> Optional.empty();
            ok = false;
        }
        LIB = lib;
        AVAILABLE = ok;
    }

    static boolean available() { return AVAILABLE; }

    private static MethodHandle bind(String symbol, FunctionDescriptor fd) {
        return LINKER.downcallHandle(
            LIB.find(symbol).orElseThrow(() -> new RuntimeException("Missing GTK symbol: " + symbol)),
            fd);
    }

    /* ---------- GLib / GObject ---------- */

    private static final MethodHandle GTK_INIT =
        bind("gtk_init", FunctionDescriptor.ofVoid());

    private static final MethodHandle G_MAIN_CONTEXT_ITERATION =
        bind("g_main_context_iteration", FunctionDescriptor.of(BOOL, PTR, BOOL));
    private static final MethodHandle G_MAIN_CONTEXT_WAKEUP =
        bind("g_main_context_wakeup", FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle G_IDLE_ADD =
        bind("g_idle_add", FunctionDescriptor.of(LONG, PTR, PTR));
    private static final MethodHandle G_OBJECT_REF =
        bind("g_object_ref", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle G_OBJECT_UNREF =
        bind("g_object_unref", FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle G_SIGNAL_CONNECT_DATA =
        bind("g_signal_connect_data", FunctionDescriptor.of(LONG, PTR, PTR, PTR, PTR, PTR, INT));
    private static final MethodHandle G_FREE =
        bind("g_free", FunctionDescriptor.ofVoid(PTR));

    /* ---------- GIO / clipboard / launcher (resolved lazily; may be in same lib path) ---------- */

    private static final MethodHandle G_APP_INFO_LAUNCH_DEFAULT_FOR_URI =
        bind("g_app_info_launch_default_for_uri", FunctionDescriptor.of(BOOL, PTR, PTR, PTR));

    private static final MethodHandle GDK_DISPLAY_GET_DEFAULT =
        bind("gdk_display_get_default", FunctionDescriptor.of(PTR));
    private static final MethodHandle GDK_DISPLAY_GET_CLIPBOARD =
        bind("gdk_display_get_clipboard", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GDK_CLIPBOARD_SET_TEXT =
        bind("gdk_clipboard_set_text", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GDK_CLIPBOARD_READ_TEXT_ASYNC =
        bind("gdk_clipboard_read_text_async", FunctionDescriptor.ofVoid(PTR, PTR, PTR, PTR));
    private static final MethodHandle GDK_CLIPBOARD_READ_TEXT_FINISH =
        bind("gdk_clipboard_read_text_finish", FunctionDescriptor.of(PTR, PTR, PTR, PTR));

    /* ---------- GTK widgets ---------- */

    private static final MethodHandle GTK_WINDOW_NEW =
        bind("gtk_window_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_WINDOW_SET_TITLE =
        bind("gtk_window_set_title", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_WINDOW_SET_DEFAULT_SIZE =
        bind("gtk_window_set_default_size", FunctionDescriptor.ofVoid(PTR, INT, INT));
    private static final MethodHandle GTK_WINDOW_SET_CHILD =
        bind("gtk_window_set_child", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_WINDOW_PRESENT =
        bind("gtk_window_present", FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle GTK_WINDOW_DESTROY =
        bind("gtk_window_destroy", FunctionDescriptor.ofVoid(PTR));

    private static final MethodHandle GTK_BUTTON_NEW_WITH_LABEL =
        bind("gtk_button_new_with_label", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_BUTTON_SET_LABEL =
        bind("gtk_button_set_label", FunctionDescriptor.ofVoid(PTR, PTR));

    private static final MethodHandle GTK_LABEL_NEW =
        bind("gtk_label_new", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_LABEL_SET_TEXT =
        bind("gtk_label_set_text", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_LABEL_GET_TEXT =
        bind("gtk_label_get_text", FunctionDescriptor.of(PTR, PTR));

    private static final MethodHandle GTK_ENTRY_NEW =
        bind("gtk_entry_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_EDITABLE_GET_TEXT =
        bind("gtk_editable_get_text", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_EDITABLE_SET_TEXT =
        bind("gtk_editable_set_text", FunctionDescriptor.ofVoid(PTR, PTR));

    private static final MethodHandle GTK_BOX_NEW =
        bind("gtk_box_new", FunctionDescriptor.of(PTR, INT, INT));
    private static final MethodHandle GTK_BOX_APPEND =
        bind("gtk_box_append", FunctionDescriptor.ofVoid(PTR, PTR));

    private static final MethodHandle GTK_WIDGET_SET_MARGIN_TOP =
        bind("gtk_widget_set_margin_top", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_WIDGET_SET_MARGIN_BOTTOM =
        bind("gtk_widget_set_margin_bottom", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_WIDGET_SET_MARGIN_START =
        bind("gtk_widget_set_margin_start", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_WIDGET_SET_MARGIN_END =
        bind("gtk_widget_set_margin_end", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_WIDGET_UNPARENT =
        bind("gtk_widget_unparent", FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle GTK_WIDGET_INSERT_ACTION_GROUP =
        bind("gtk_widget_insert_action_group", FunctionDescriptor.ofVoid(PTR, PTR, PTR));
    private static final MethodHandle GTK_WIDGET_SET_TOOLTIP_TEXT =
        bind("gtk_widget_set_tooltip_text", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_WIDGET_ADD_CONTROLLER =
        bind("gtk_widget_add_controller", FunctionDescriptor.ofVoid(PTR, PTR));

    /* ---------- Drag and drop ---------- */

    private static final MethodHandle GTK_DRAG_SOURCE_NEW =
        bind("gtk_drag_source_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_DRAG_SOURCE_SET_ACTIONS =
        bind("gtk_drag_source_set_actions", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_DROP_TARGET_NEW =
        bind("gtk_drop_target_new", FunctionDescriptor.of(PTR, LONG, INT));
    private static final MethodHandle GDK_CONTENT_PROVIDER_NEW_FOR_VALUE =
        bind("gdk_content_provider_new_for_value", FunctionDescriptor.of(PTR, PTR));

    /* ---------- GValue ---------- */

    private static final MethodHandle G_VALUE_INIT =
        bind("g_value_init", FunctionDescriptor.of(PTR, PTR, LONG));
    private static final MethodHandle G_VALUE_SET_STRING =
        bind("g_value_set_string", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle G_VALUE_GET_STRING =
        bind("g_value_get_string", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle G_VALUE_UNSET =
        bind("g_value_unset", FunctionDescriptor.ofVoid(PTR));

    /* ---------- GListStore / GListModel / GtkStringObject ---------- */

    private static final MethodHandle G_LIST_STORE_NEW =
        bind("g_list_store_new", FunctionDescriptor.of(PTR, LONG));
    private static final MethodHandle G_LIST_STORE_APPEND =
        bind("g_list_store_append", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle G_LIST_STORE_REMOVE_ALL =
        bind("g_list_store_remove_all", FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle G_LIST_MODEL_GET_N_ITEMS =
        bind("g_list_model_get_n_items", FunctionDescriptor.of(INT, PTR));
    private static final MethodHandle G_LIST_MODEL_GET_ITEM =
        bind("g_list_model_get_item", FunctionDescriptor.of(PTR, PTR, INT));
    private static final MethodHandle GTK_STRING_OBJECT_NEW =
        bind("gtk_string_object_new", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_STRING_OBJECT_GET_STRING =
        bind("gtk_string_object_get_string", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_STRING_OBJECT_GET_TYPE =
        bind("gtk_string_object_get_type", FunctionDescriptor.of(LONG));

    /* ---------- GtkTreeListModel / Row / SingleSelection / ColumnView ---------- */

    private static final MethodHandle GTK_TREE_LIST_MODEL_NEW =
        bind("gtk_tree_list_model_new", FunctionDescriptor.of(PTR, PTR, BOOL, BOOL, PTR, PTR, PTR));
    private static final MethodHandle GTK_TREE_LIST_ROW_GET_ITEM =
        bind("gtk_tree_list_row_get_item", FunctionDescriptor.of(PTR, PTR));

    private static final MethodHandle GTK_SINGLE_SELECTION_NEW =
        bind("gtk_single_selection_new", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_SINGLE_SELECTION_GET_SELECTED =
        bind("gtk_single_selection_get_selected", FunctionDescriptor.of(INT, PTR));
    private static final MethodHandle GTK_SINGLE_SELECTION_SET_SELECTED =
        bind("gtk_single_selection_set_selected", FunctionDescriptor.ofVoid(PTR, INT));

    private static final MethodHandle GTK_COLUMN_VIEW_NEW =
        bind("gtk_column_view_new", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_COLUMN_VIEW_APPEND_COLUMN =
        bind("gtk_column_view_append_column", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_COLUMN_VIEW_SET_HEADERS_VISIBLE =
        bind("gtk_column_view_set_show_column_separators",
            FunctionDescriptor.ofVoid(PTR, BOOL));

    private static final MethodHandle GTK_COLUMN_VIEW_COLUMN_NEW =
        bind("gtk_column_view_column_new", FunctionDescriptor.of(PTR, PTR, PTR));
    private static final MethodHandle GTK_COLUMN_VIEW_COLUMN_SET_EXPAND =
        bind("gtk_column_view_column_set_expand", FunctionDescriptor.ofVoid(PTR, BOOL));

    private static final MethodHandle GTK_SIGNAL_LIST_ITEM_FACTORY_NEW =
        bind("gtk_signal_list_item_factory_new", FunctionDescriptor.of(PTR));

    private static final MethodHandle GTK_LIST_ITEM_GET_ITEM =
        bind("gtk_list_item_get_item", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_LIST_ITEM_SET_CHILD =
        bind("gtk_list_item_set_child", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_LIST_ITEM_GET_CHILD =
        bind("gtk_list_item_get_child", FunctionDescriptor.of(PTR, PTR));

    private static final MethodHandle GTK_TREE_EXPANDER_NEW =
        bind("gtk_tree_expander_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_TREE_EXPANDER_SET_LIST_ROW =
        bind("gtk_tree_expander_set_list_row", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_TREE_EXPANDER_SET_CHILD =
        bind("gtk_tree_expander_set_child", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_TREE_EXPANDER_GET_CHILD =
        bind("gtk_tree_expander_get_child", FunctionDescriptor.of(PTR, PTR));

    private static final MethodHandle GTK_SCROLLED_WINDOW_NEW =
        bind("gtk_scrolled_window_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_SCROLLED_WINDOW_SET_CHILD =
        bind("gtk_scrolled_window_set_child", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_SCROLLED_WINDOW_SET_POLICY =
        bind("gtk_scrolled_window_set_policy", FunctionDescriptor.ofVoid(PTR, INT, INT));
    private static final MethodHandle GTK_SCROLLED_WINDOW_SET_MIN_CONTENT_HEIGHT =
        bind("gtk_scrolled_window_set_min_content_height", FunctionDescriptor.ofVoid(PTR, INT));

    private static final MethodHandle GTK_LIST_BOX_NEW =
        bind("gtk_list_box_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_LIST_BOX_APPEND =
        bind("gtk_list_box_append", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_LIST_BOX_REMOVE =
        bind("gtk_list_box_remove", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_LIST_BOX_SELECT_ROW =
        bind("gtk_list_box_select_row", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_LIST_BOX_UNSELECT_ALL =
        bind("gtk_list_box_unselect_all", FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle GTK_LIST_BOX_GET_SELECTED_ROW =
        bind("gtk_list_box_get_selected_row", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_LIST_BOX_GET_ROW_AT_INDEX =
        bind("gtk_list_box_get_row_at_index", FunctionDescriptor.of(PTR, PTR, INT));
    private static final MethodHandle GTK_LIST_BOX_ROW_GET_INDEX =
        bind("gtk_list_box_row_get_index", FunctionDescriptor.of(INT, PTR));
    private static final MethodHandle GTK_LIST_BOX_SET_SELECTION_MODE =
        bind("gtk_list_box_set_selection_mode", FunctionDescriptor.ofVoid(PTR, INT));

    private static final MethodHandle GTK_POPOVER_MENU_BAR_NEW_FROM_MODEL =
        bind("gtk_popover_menu_bar_new_from_model", FunctionDescriptor.of(PTR, PTR));

    /* ---------- Toggle controls (Checkbox / Switch / Radio) ---------- */

    private static final MethodHandle GTK_CHECK_BUTTON_NEW_WITH_LABEL =
        bind("gtk_check_button_new_with_label", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_CHECK_BUTTON_SET_LABEL =
        bind("gtk_check_button_set_label", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_CHECK_BUTTON_SET_ACTIVE =
        bind("gtk_check_button_set_active", FunctionDescriptor.ofVoid(PTR, BOOL));
    private static final MethodHandle GTK_CHECK_BUTTON_GET_ACTIVE =
        bind("gtk_check_button_get_active", FunctionDescriptor.of(BOOL, PTR));
    private static final MethodHandle GTK_CHECK_BUTTON_SET_GROUP =
        bind("gtk_check_button_set_group", FunctionDescriptor.ofVoid(PTR, PTR));

    private static final MethodHandle GTK_SWITCH_NEW =
        bind("gtk_switch_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_SWITCH_SET_ACTIVE =
        bind("gtk_switch_set_active", FunctionDescriptor.ofVoid(PTR, BOOL));
    private static final MethodHandle GTK_SWITCH_GET_ACTIVE =
        bind("gtk_switch_get_active", FunctionDescriptor.of(BOOL, PTR));

    /* ---------- Numeric controls (Slider / ProgressBar / Spinner) ---------- */

    private static final MethodHandle GTK_SCALE_NEW_WITH_RANGE =
        bind("gtk_scale_new_with_range", FunctionDescriptor.of(PTR, INT,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle GTK_RANGE_SET_RANGE =
        bind("gtk_range_set_range", FunctionDescriptor.ofVoid(PTR,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle GTK_RANGE_SET_VALUE =
        bind("gtk_range_set_value", FunctionDescriptor.ofVoid(PTR, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle GTK_RANGE_GET_VALUE =
        bind("gtk_range_get_value", FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, PTR));

    private static final MethodHandle GTK_PROGRESS_BAR_NEW =
        bind("gtk_progress_bar_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_PROGRESS_BAR_SET_FRACTION =
        bind("gtk_progress_bar_set_fraction", FunctionDescriptor.ofVoid(PTR, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle GTK_PROGRESS_BAR_PULSE =
        bind("gtk_progress_bar_pulse", FunctionDescriptor.ofVoid(PTR));

    private static final MethodHandle GTK_SPINNER_NEW =
        bind("gtk_spinner_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_SPINNER_START =
        bind("gtk_spinner_start", FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle GTK_SPINNER_STOP =
        bind("gtk_spinner_stop", FunctionDescriptor.ofVoid(PTR));

    private static final MethodHandle G_TIMEOUT_ADD =
        bind("g_timeout_add", FunctionDescriptor.of(LONG, INT, PTR, PTR));
    private static final MethodHandle G_SOURCE_REMOVE =
        bind("g_source_remove", FunctionDescriptor.of(BOOL, LONG));

    /* ---------- DropDown ---------- */

    private static final MethodHandle GTK_DROP_DOWN_NEW_FROM_STRINGS =
        bind("gtk_drop_down_new_from_strings", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_DROP_DOWN_SET_MODEL =
        bind("gtk_drop_down_set_model", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_DROP_DOWN_GET_SELECTED =
        bind("gtk_drop_down_get_selected", FunctionDescriptor.of(INT, PTR));
    private static final MethodHandle GTK_DROP_DOWN_SET_SELECTED =
        bind("gtk_drop_down_set_selected", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_STRING_LIST_NEW =
        bind("gtk_string_list_new", FunctionDescriptor.of(PTR, PTR));

    /* ---------- Containers ---------- */

    private static final MethodHandle GTK_FRAME_NEW =
        bind("gtk_frame_new", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_FRAME_SET_LABEL =
        bind("gtk_frame_set_label", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_FRAME_SET_CHILD =
        bind("gtk_frame_set_child", FunctionDescriptor.ofVoid(PTR, PTR));

    private static final MethodHandle GTK_NOTEBOOK_NEW =
        bind("gtk_notebook_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_NOTEBOOK_APPEND_PAGE =
        bind("gtk_notebook_append_page", FunctionDescriptor.of(INT, PTR, PTR, PTR));
    private static final MethodHandle GTK_NOTEBOOK_SET_CURRENT_PAGE =
        bind("gtk_notebook_set_current_page", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_NOTEBOOK_GET_CURRENT_PAGE =
        bind("gtk_notebook_get_current_page", FunctionDescriptor.of(INT, PTR));

    private static final MethodHandle GTK_PANED_NEW =
        bind("gtk_paned_new", FunctionDescriptor.of(PTR, INT));
    private static final MethodHandle GTK_PANED_SET_START_CHILD =
        bind("gtk_paned_set_start_child", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_PANED_SET_END_CHILD =
        bind("gtk_paned_set_end_child", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_PANED_SET_POSITION =
        bind("gtk_paned_set_position", FunctionDescriptor.ofVoid(PTR, INT));

    private static final MethodHandle GTK_EXPANDER_NEW_WITH_LABEL =
        bind("gtk_expander_new_with_label", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle GTK_EXPANDER_SET_LABEL =
        bind("gtk_expander_set_label", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_EXPANDER_SET_CHILD =
        bind("gtk_expander_set_child", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_EXPANDER_SET_EXPANDED =
        bind("gtk_expander_set_expanded", FunctionDescriptor.ofVoid(PTR, BOOL));
    private static final MethodHandle GTK_EXPANDER_GET_EXPANDED =
        bind("gtk_expander_get_expanded", FunctionDescriptor.of(BOOL, PTR));

    /* ---------- Image (GtkPicture) ---------- */

    private static final MethodHandle GTK_PICTURE_NEW =
        bind("gtk_picture_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_PICTURE_SET_FILENAME =
        bind("gtk_picture_set_filename", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_PICTURE_SET_PAINTABLE =
        bind("gtk_picture_set_paintable", FunctionDescriptor.ofVoid(PTR, PTR));

    /* ---------- GBytes / GdkTexture (in-memory image decode) ---------- */

    private static final MethodHandle G_BYTES_NEW =
        bind("g_bytes_new", FunctionDescriptor.of(PTR, PTR, LONG));
    private static final MethodHandle G_BYTES_UNREF =
        bind("g_bytes_unref", FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle GDK_TEXTURE_NEW_FROM_BYTES =
        bind("gdk_texture_new_from_bytes", FunctionDescriptor.of(PTR, PTR, PTR));

    /* ---------- Canvas (GtkDrawingArea) ---------- */

    private static final MethodHandle GTK_DRAWING_AREA_NEW =
        bind("gtk_drawing_area_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_DRAWING_AREA_SET_CONTENT_WIDTH =
        bind("gtk_drawing_area_set_content_width", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_DRAWING_AREA_SET_CONTENT_HEIGHT =
        bind("gtk_drawing_area_set_content_height", FunctionDescriptor.ofVoid(PTR, INT));
    private static final MethodHandle GTK_DRAWING_AREA_SET_DRAW_FUNC =
        bind("gtk_drawing_area_set_draw_func", FunctionDescriptor.ofVoid(PTR, PTR, PTR, PTR));
    private static final MethodHandle GTK_WIDGET_QUEUE_DRAW =
        bind("gtk_widget_queue_draw", FunctionDescriptor.ofVoid(PTR));

    /* ---------- GtkFileDialog (4.10+) ---------- */

    private static final MethodHandle GTK_FILE_DIALOG_NEW =
        bind("gtk_file_dialog_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle GTK_FILE_DIALOG_SET_TITLE =
        bind("gtk_file_dialog_set_title", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_FILE_DIALOG_SET_INITIAL_FOLDER =
        bind("gtk_file_dialog_set_initial_folder", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_FILE_DIALOG_SET_INITIAL_NAME =
        bind("gtk_file_dialog_set_initial_name", FunctionDescriptor.ofVoid(PTR, PTR));
    private static final MethodHandle GTK_FILE_DIALOG_OPEN =
        bind("gtk_file_dialog_open", FunctionDescriptor.ofVoid(PTR, PTR, PTR, PTR, PTR));
    private static final MethodHandle GTK_FILE_DIALOG_OPEN_FINISH =
        bind("gtk_file_dialog_open_finish", FunctionDescriptor.of(PTR, PTR, PTR, PTR));
    private static final MethodHandle GTK_FILE_DIALOG_SAVE =
        bind("gtk_file_dialog_save", FunctionDescriptor.ofVoid(PTR, PTR, PTR, PTR, PTR));
    private static final MethodHandle GTK_FILE_DIALOG_SAVE_FINISH =
        bind("gtk_file_dialog_save_finish", FunctionDescriptor.of(PTR, PTR, PTR, PTR));
    private static final MethodHandle GTK_FILE_DIALOG_SELECT_FOLDER =
        bind("gtk_file_dialog_select_folder", FunctionDescriptor.ofVoid(PTR, PTR, PTR, PTR, PTR));
    private static final MethodHandle GTK_FILE_DIALOG_SELECT_FOLDER_FINISH =
        bind("gtk_file_dialog_select_folder_finish", FunctionDescriptor.of(PTR, PTR, PTR, PTR));

    private static final MethodHandle G_FILE_NEW_FOR_PATH =
        bind("g_file_new_for_path", FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle G_FILE_GET_PATH =
        bind("g_file_get_path", FunctionDescriptor.of(PTR, PTR));

    /* ---------- GLib / GIO menus & actions ---------- */

    private static final MethodHandle G_MENU_NEW =
        bind("g_menu_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle G_MENU_APPEND =
        bind("g_menu_append", FunctionDescriptor.ofVoid(PTR, PTR, PTR));
    private static final MethodHandle G_MENU_APPEND_SUBMENU =
        bind("g_menu_append_submenu", FunctionDescriptor.ofVoid(PTR, PTR, PTR));
    private static final MethodHandle G_MENU_APPEND_SECTION =
        bind("g_menu_append_section", FunctionDescriptor.ofVoid(PTR, PTR, PTR));
    private static final MethodHandle G_MENU_REMOVE_ALL =
        bind("g_menu_remove_all", FunctionDescriptor.ofVoid(PTR));

    private static final MethodHandle G_SIMPLE_ACTION_GROUP_NEW =
        bind("g_simple_action_group_new", FunctionDescriptor.of(PTR));
    private static final MethodHandle G_SIMPLE_ACTION_NEW =
        bind("g_simple_action_new", FunctionDescriptor.of(PTR, PTR, PTR));
    private static final MethodHandle G_SIMPLE_ACTION_SET_ENABLED =
        bind("g_simple_action_set_enabled", FunctionDescriptor.ofVoid(PTR, BOOL));
    private static final MethodHandle G_ACTION_MAP_ADD_ACTION =
        bind("g_action_map_add_action", FunctionDescriptor.ofVoid(PTR, PTR));

    /* ---------- Wrappers ---------- */

    static void gtk_init() { call(GTK_INIT); }

    static boolean g_main_context_iteration(MemorySegment context, boolean mayBlock) {
        try { return ((int) G_MAIN_CONTEXT_ITERATION.invoke(context, mayBlock ? 1 : 0)) != 0; }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_main_context_wakeup(MemorySegment context) { callPtr(G_MAIN_CONTEXT_WAKEUP, context); }

    static long g_idle_add(MemorySegment func, MemorySegment data) {
        try { return (long) G_IDLE_ADD.invoke(func, data); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment g_object_ref(MemorySegment obj) {
        try { return (MemorySegment) G_OBJECT_REF.invoke(obj); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_object_unref(MemorySegment obj) { callPtr(G_OBJECT_UNREF, obj); }

    static long g_signal_connect_data(MemorySegment instance, String signal, MemorySegment callback,
                                      MemorySegment data, MemorySegment destroy, int flags) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment sig = arena.allocateFrom(signal);
            return (long) G_SIGNAL_CONNECT_DATA.invoke(instance, sig, callback, data, destroy, flags);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_window_new() {
        try { return (MemorySegment) GTK_WINDOW_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_window_set_title(MemorySegment win, String title) {
        try (var arena = Arena.ofConfined()) {
            callPtrPtr(GTK_WINDOW_SET_TITLE, win, arena.allocateFrom(title));
        }
    }

    static void gtk_window_set_default_size(MemorySegment win, int w, int h) {
        try { GTK_WINDOW_SET_DEFAULT_SIZE.invoke(win, w, h); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_window_set_child(MemorySegment win, MemorySegment child) {
        callPtrPtr(GTK_WINDOW_SET_CHILD, win, child);
    }

    static void gtk_window_present(MemorySegment win) { callPtr(GTK_WINDOW_PRESENT, win); }

    static void gtk_window_destroy(MemorySegment win) { callPtr(GTK_WINDOW_DESTROY, win); }

    static MemorySegment gtk_button_new_with_label(String label) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) GTK_BUTTON_NEW_WITH_LABEL.invoke(arena.allocateFrom(label));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_button_set_label(MemorySegment btn, String label) {
        try (var arena = Arena.ofConfined()) {
            callPtrPtr(GTK_BUTTON_SET_LABEL, btn, arena.allocateFrom(label));
        }
    }

    static MemorySegment gtk_label_new(String text) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) GTK_LABEL_NEW.invoke(arena.allocateFrom(text == null ? "" : text));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_label_set_text(MemorySegment lbl, String text) {
        try (var arena = Arena.ofConfined()) {
            callPtrPtr(GTK_LABEL_SET_TEXT, lbl, arena.allocateFrom(text == null ? "" : text));
        }
    }

    static String gtk_label_get_text(MemorySegment lbl) {
        try {
            MemorySegment cstr = (MemorySegment) GTK_LABEL_GET_TEXT.invoke(lbl);
            return readCString(cstr);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_entry_new() {
        try { return (MemorySegment) GTK_ENTRY_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static String gtk_editable_get_text(MemorySegment entry) {
        try {
            MemorySegment cstr = (MemorySegment) GTK_EDITABLE_GET_TEXT.invoke(entry);
            return readCString(cstr);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_editable_set_text(MemorySegment entry, String text) {
        try (var arena = Arena.ofConfined()) {
            callPtrPtr(GTK_EDITABLE_SET_TEXT, entry, arena.allocateFrom(text == null ? "" : text));
        }
    }

    static MemorySegment gtk_box_new(int orientation, int spacing) {
        try { return (MemorySegment) GTK_BOX_NEW.invoke(orientation, spacing); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_box_append(MemorySegment box, MemorySegment child) {
        callPtrPtr(GTK_BOX_APPEND, box, child);
    }

    static void gtk_widget_set_margin_top(MemorySegment w, int m) { callPtrInt(GTK_WIDGET_SET_MARGIN_TOP, w, m); }
    static void gtk_widget_set_margin_bottom(MemorySegment w, int m) { callPtrInt(GTK_WIDGET_SET_MARGIN_BOTTOM, w, m); }
    static void gtk_widget_set_margin_start(MemorySegment w, int m) { callPtrInt(GTK_WIDGET_SET_MARGIN_START, w, m); }
    static void gtk_widget_set_margin_end(MemorySegment w, int m) { callPtrInt(GTK_WIDGET_SET_MARGIN_END, w, m); }

    static void gtk_widget_unparent(MemorySegment w) { callPtr(GTK_WIDGET_UNPARENT, w); }

    static void gtk_widget_set_tooltip_text(MemorySegment w, String text) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment t = (text == null || text.isEmpty()) ? MemorySegment.NULL : arena.allocateFrom(text);
            callPtrPtr(GTK_WIDGET_SET_TOOLTIP_TEXT, w, t);
        }
    }

    static void gtk_widget_add_controller(MemorySegment w, MemorySegment controller) {
        callPtrPtr(GTK_WIDGET_ADD_CONTROLLER, w, controller);
    }

    /* ---------- DnD wrappers ---------- */

    /** GLib fundamental type ID for {@code G_TYPE_STRING} (16 << G_TYPE_FUNDAMENTAL_SHIFT). */
    static final long G_TYPE_STRING = 16L << 2;
    /** {@code GDK_ACTION_COPY}: 1 << 0. */
    static final int GDK_ACTION_COPY = 1;

    /** Layout of {@code GValue} (gtype + 2-element data union of 8-byte slots). */
    static final java.lang.foreign.MemoryLayout G_VALUE = java.lang.foreign.MemoryLayout.structLayout(
        LONG.withName("g_type"),
        LONG.withName("data0"),
        LONG.withName("data1"));

    static MemorySegment gtk_drag_source_new() {
        try { return (MemorySegment) GTK_DRAG_SOURCE_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_drag_source_set_actions(MemorySegment source, int actions) {
        try { GTK_DRAG_SOURCE_SET_ACTIONS.invoke(source, actions); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_drop_target_new(long gtype, int actions) {
        try { return (MemorySegment) GTK_DROP_TARGET_NEW.invoke(gtype, actions); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gdk_content_provider_new_for_value(MemorySegment value) {
        try { return (MemorySegment) GDK_CONTENT_PROVIDER_NEW_FOR_VALUE.invoke(value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_value_init(MemorySegment value, long gtype) {
        try { G_VALUE_INIT.invoke(value, gtype); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_value_set_string(MemorySegment value, MemorySegment cstr) {
        try { G_VALUE_SET_STRING.invoke(value, cstr); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment g_value_get_string(MemorySegment value) {
        try { return (MemorySegment) G_VALUE_GET_STRING.invoke(value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_value_unset(MemorySegment value) {
        try { G_VALUE_UNSET.invoke(value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /* ---------- GListStore / GListModel / GtkStringObject ---------- */

    static MemorySegment g_list_store_new(long itemType) {
        try { return (MemorySegment) G_LIST_STORE_NEW.invoke(itemType); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_list_store_append(MemorySegment store, MemorySegment item) {
        callPtrPtr(G_LIST_STORE_APPEND, store, item);
    }

    static void g_list_store_remove_all(MemorySegment store) {
        callPtr(G_LIST_STORE_REMOVE_ALL, store);
    }

    static int g_list_model_get_n_items(MemorySegment model) {
        try { return (int) G_LIST_MODEL_GET_N_ITEMS.invoke(model); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment g_list_model_get_item(MemorySegment model, int position) {
        try { return (MemorySegment) G_LIST_MODEL_GET_ITEM.invoke(model, position); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_string_object_new(String text) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) GTK_STRING_OBJECT_NEW.invoke(
                arena.allocateFrom(text == null ? "" : text));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static String gtk_string_object_get_string(MemorySegment obj) {
        try {
            MemorySegment cstr = (MemorySegment) GTK_STRING_OBJECT_GET_STRING.invoke(obj);
            return readCString(cstr);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static long gtk_string_object_get_type() {
        try { return (long) GTK_STRING_OBJECT_GET_TYPE.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /* ---------- GtkTreeListModel / SingleSelection / ColumnView ---------- */

    static MemorySegment gtk_tree_list_model_new(MemorySegment root, boolean passthrough,
                                                 boolean autoexpand, MemorySegment createFunc,
                                                 MemorySegment userData, MemorySegment userDestroy) {
        try {
            return (MemorySegment) GTK_TREE_LIST_MODEL_NEW.invoke(
                root, passthrough ? 1 : 0, autoexpand ? 1 : 0,
                createFunc, userData, userDestroy);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_tree_list_row_get_item(MemorySegment row) {
        try { return (MemorySegment) GTK_TREE_LIST_ROW_GET_ITEM.invoke(row); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_single_selection_new(MemorySegment model) {
        try { return (MemorySegment) GTK_SINGLE_SELECTION_NEW.invoke(model); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static int gtk_single_selection_get_selected(MemorySegment self) {
        try { return (int) GTK_SINGLE_SELECTION_GET_SELECTED.invoke(self); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_single_selection_set_selected(MemorySegment self, int position) {
        try { GTK_SINGLE_SELECTION_SET_SELECTED.invoke(self, position); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_column_view_new(MemorySegment selectionModel) {
        try { return (MemorySegment) GTK_COLUMN_VIEW_NEW.invoke(selectionModel); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_column_view_append_column(MemorySegment view, MemorySegment column) {
        callPtrPtr(GTK_COLUMN_VIEW_APPEND_COLUMN, view, column);
    }

    static void gtk_column_view_set_show_column_separators(MemorySegment view, boolean show) {
        try { GTK_COLUMN_VIEW_SET_HEADERS_VISIBLE.invoke(view, show ? 1 : 0); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_column_view_column_new(String title, MemorySegment factory) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment t = title == null ? MemorySegment.NULL : arena.allocateFrom(title);
            return (MemorySegment) GTK_COLUMN_VIEW_COLUMN_NEW.invoke(t, factory);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_column_view_column_set_expand(MemorySegment column, boolean expand) {
        try { GTK_COLUMN_VIEW_COLUMN_SET_EXPAND.invoke(column, expand ? 1 : 0); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_signal_list_item_factory_new() {
        try { return (MemorySegment) GTK_SIGNAL_LIST_ITEM_FACTORY_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_list_item_get_item(MemorySegment item) {
        try { return (MemorySegment) GTK_LIST_ITEM_GET_ITEM.invoke(item); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_list_item_set_child(MemorySegment item, MemorySegment widget) {
        callPtrPtr(GTK_LIST_ITEM_SET_CHILD, item, widget);
    }

    static MemorySegment gtk_list_item_get_child(MemorySegment item) {
        try { return (MemorySegment) GTK_LIST_ITEM_GET_CHILD.invoke(item); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_tree_expander_new() {
        try { return (MemorySegment) GTK_TREE_EXPANDER_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_tree_expander_set_list_row(MemorySegment expander, MemorySegment row) {
        callPtrPtr(GTK_TREE_EXPANDER_SET_LIST_ROW, expander, row);
    }

    static void gtk_tree_expander_set_child(MemorySegment expander, MemorySegment child) {
        callPtrPtr(GTK_TREE_EXPANDER_SET_CHILD, expander, child);
    }

    static MemorySegment gtk_tree_expander_get_child(MemorySegment expander) {
        try { return (MemorySegment) GTK_TREE_EXPANDER_GET_CHILD.invoke(expander); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static boolean g_app_info_launch_default_for_uri(String uri) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment u = arena.allocateFrom(uri);
            return ((int) G_APP_INFO_LAUNCH_DEFAULT_FOR_URI.invoke(u, MemorySegment.NULL, MemorySegment.NULL)) != 0;
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gdk_display_get_default() {
        try { return (MemorySegment) GDK_DISPLAY_GET_DEFAULT.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gdk_display_get_clipboard(MemorySegment display) {
        try { return (MemorySegment) GDK_DISPLAY_GET_CLIPBOARD.invoke(display); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gdk_clipboard_set_text(MemorySegment cb, String text) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment t = arena.allocateFrom(text == null ? "" : text);
            callPtrPtr(GDK_CLIPBOARD_SET_TEXT, cb, t);
        }
    }

    static void gdk_clipboard_read_text_async(MemorySegment clipboard, MemorySegment cancellable,
                                              MemorySegment callback, MemorySegment userData) {
        try { GDK_CLIPBOARD_READ_TEXT_ASYNC.invoke(clipboard, cancellable, callback, userData); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Returns a transfer-full {@code char*} that the caller must {@link #g_free}. */
    static MemorySegment gdk_clipboard_read_text_finish(MemorySegment clipboard, MemorySegment result,
                                                       MemorySegment errorOut) {
        try { return (MemorySegment) GDK_CLIPBOARD_READ_TEXT_FINISH.invoke(clipboard, result, errorOut); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_free(MemorySegment p) { callPtr(G_FREE, p); }

    static void gtk_widget_insert_action_group(MemorySegment widget, String name, MemorySegment group) {
        try (var arena = Arena.ofConfined()) {
            try { GTK_WIDGET_INSERT_ACTION_GROUP.invoke(widget, arena.allocateFrom(name), group); }
            catch (Throwable t) { throw new RuntimeException(t); }
        }
    }

    static MemorySegment gtk_scrolled_window_new() {
        try { return (MemorySegment) GTK_SCROLLED_WINDOW_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_scrolled_window_set_child(MemorySegment sw, MemorySegment child) {
        callPtrPtr(GTK_SCROLLED_WINDOW_SET_CHILD, sw, child);
    }

    static void gtk_scrolled_window_set_policy(MemorySegment sw, int hPolicy, int vPolicy) {
        try { GTK_SCROLLED_WINDOW_SET_POLICY.invoke(sw, hPolicy, vPolicy); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_scrolled_window_set_min_content_height(MemorySegment sw, int h) {
        callPtrInt(GTK_SCROLLED_WINDOW_SET_MIN_CONTENT_HEIGHT, sw, h);
    }

    static MemorySegment gtk_list_box_new() {
        try { return (MemorySegment) GTK_LIST_BOX_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_list_box_append(MemorySegment box, MemorySegment child) {
        callPtrPtr(GTK_LIST_BOX_APPEND, box, child);
    }

    static void gtk_list_box_remove(MemorySegment box, MemorySegment child) {
        callPtrPtr(GTK_LIST_BOX_REMOVE, box, child);
    }

    static void gtk_list_box_select_row(MemorySegment box, MemorySegment row) {
        callPtrPtr(GTK_LIST_BOX_SELECT_ROW, box, row);
    }

    static void gtk_list_box_unselect_all(MemorySegment box) { callPtr(GTK_LIST_BOX_UNSELECT_ALL, box); }

    static MemorySegment gtk_list_box_get_selected_row(MemorySegment box) {
        try { return (MemorySegment) GTK_LIST_BOX_GET_SELECTED_ROW.invoke(box); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_list_box_get_row_at_index(MemorySegment box, int index) {
        try { return (MemorySegment) GTK_LIST_BOX_GET_ROW_AT_INDEX.invoke(box, index); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static int gtk_list_box_row_get_index(MemorySegment row) {
        try { return (int) GTK_LIST_BOX_ROW_GET_INDEX.invoke(row); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_list_box_set_selection_mode(MemorySegment box, int mode) {
        try { GTK_LIST_BOX_SET_SELECTION_MODE.invoke(box, mode); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_popover_menu_bar_new_from_model(MemorySegment menuModel) {
        try { return (MemorySegment) GTK_POPOVER_MENU_BAR_NEW_FROM_MODEL.invoke(menuModel); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_check_button_new_with_label(String label) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) GTK_CHECK_BUTTON_NEW_WITH_LABEL.invoke(arena.allocateFrom(label == null ? "" : label));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_check_button_set_label(MemorySegment cb, String label) {
        try (var arena = Arena.ofConfined()) {
            callPtrPtr(GTK_CHECK_BUTTON_SET_LABEL, cb, arena.allocateFrom(label == null ? "" : label));
        }
    }

    static void gtk_check_button_set_active(MemorySegment cb, boolean active) {
        try { GTK_CHECK_BUTTON_SET_ACTIVE.invoke(cb, active ? 1 : 0); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static boolean gtk_check_button_get_active(MemorySegment cb) {
        try { return ((int) GTK_CHECK_BUTTON_GET_ACTIVE.invoke(cb)) != 0; }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_check_button_set_group(MemorySegment cb, MemorySegment groupSource) {
        callPtrPtr(GTK_CHECK_BUTTON_SET_GROUP, cb, groupSource);
    }

    static MemorySegment gtk_switch_new() {
        try { return (MemorySegment) GTK_SWITCH_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_switch_set_active(MemorySegment sw, boolean active) {
        try { GTK_SWITCH_SET_ACTIVE.invoke(sw, active ? 1 : 0); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static boolean gtk_switch_get_active(MemorySegment sw) {
        try { return ((int) GTK_SWITCH_GET_ACTIVE.invoke(sw)) != 0; }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_scale_new_with_range(int orientation, double min, double max, double step) {
        try { return (MemorySegment) GTK_SCALE_NEW_WITH_RANGE.invoke(orientation, min, max, step); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_range_set_range(MemorySegment r, double min, double max) {
        try { GTK_RANGE_SET_RANGE.invoke(r, min, max); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_range_set_value(MemorySegment r, double v) {
        try { GTK_RANGE_SET_VALUE.invoke(r, v); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static double gtk_range_get_value(MemorySegment r) {
        try { return (double) GTK_RANGE_GET_VALUE.invoke(r); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_progress_bar_new() {
        try { return (MemorySegment) GTK_PROGRESS_BAR_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_progress_bar_set_fraction(MemorySegment pb, double v) {
        try { GTK_PROGRESS_BAR_SET_FRACTION.invoke(pb, v); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_progress_bar_pulse(MemorySegment pb) { callPtr(GTK_PROGRESS_BAR_PULSE, pb); }

    static MemorySegment gtk_spinner_new() {
        try { return (MemorySegment) GTK_SPINNER_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_spinner_start(MemorySegment sp) { callPtr(GTK_SPINNER_START, sp); }
    static void gtk_spinner_stop(MemorySegment sp) { callPtr(GTK_SPINNER_STOP, sp); }

    static long g_timeout_add(int ms, MemorySegment func, MemorySegment data) {
        try { return (long) G_TIMEOUT_ADD.invoke(ms, func, data); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static boolean g_source_remove(long id) {
        try { return ((int) G_SOURCE_REMOVE.invoke(id)) != 0; }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Allocate a NULL-terminated char** array from {@code items} into {@code arena}. */
    static MemorySegment makeStringArray(Arena arena, java.util.List<String> items) {
        MemorySegment arr = arena.allocate(ValueLayout.ADDRESS, items.size() + 1L);
        for (int i = 0; i < items.size(); i++) {
            String s = items.get(i);
            arr.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(s == null ? "" : s));
        }
        arr.setAtIndex(ValueLayout.ADDRESS, items.size(), MemorySegment.NULL);
        return arr;
    }

    static MemorySegment gtk_drop_down_new_from_strings(java.util.List<String> items) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment arr = makeStringArray(arena, items);
            return (MemorySegment) GTK_DROP_DOWN_NEW_FROM_STRINGS.invoke(arr);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_string_list_new(java.util.List<String> items) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment arr = makeStringArray(arena, items);
            return (MemorySegment) GTK_STRING_LIST_NEW.invoke(arr);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_drop_down_set_model(MemorySegment dd, MemorySegment model) {
        callPtrPtr(GTK_DROP_DOWN_SET_MODEL, dd, model);
    }

    static int gtk_drop_down_get_selected(MemorySegment dd) {
        try { return (int) GTK_DROP_DOWN_GET_SELECTED.invoke(dd); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_drop_down_set_selected(MemorySegment dd, int index) {
        try { GTK_DROP_DOWN_SET_SELECTED.invoke(dd, index); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_frame_new(String label) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment lbl = label == null ? MemorySegment.NULL : arena.allocateFrom(label);
            return (MemorySegment) GTK_FRAME_NEW.invoke(lbl);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_frame_set_label(MemorySegment frame, String label) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment lbl = label == null ? MemorySegment.NULL : arena.allocateFrom(label);
            callPtrPtr(GTK_FRAME_SET_LABEL, frame, lbl);
        }
    }

    static void gtk_frame_set_child(MemorySegment frame, MemorySegment child) {
        callPtrPtr(GTK_FRAME_SET_CHILD, frame, child);
    }

    static MemorySegment gtk_notebook_new() {
        try { return (MemorySegment) GTK_NOTEBOOK_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static int gtk_notebook_append_page(MemorySegment book, MemorySegment child, MemorySegment tabLabel) {
        try { return (int) GTK_NOTEBOOK_APPEND_PAGE.invoke(book, child, tabLabel); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_notebook_set_current_page(MemorySegment book, int idx) {
        try { GTK_NOTEBOOK_SET_CURRENT_PAGE.invoke(book, idx); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static int gtk_notebook_get_current_page(MemorySegment book) {
        try { return (int) GTK_NOTEBOOK_GET_CURRENT_PAGE.invoke(book); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_paned_new(int orientation) {
        try { return (MemorySegment) GTK_PANED_NEW.invoke(orientation); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_paned_set_start_child(MemorySegment paned, MemorySegment child) {
        callPtrPtr(GTK_PANED_SET_START_CHILD, paned, child);
    }

    static void gtk_paned_set_end_child(MemorySegment paned, MemorySegment child) {
        callPtrPtr(GTK_PANED_SET_END_CHILD, paned, child);
    }

    static void gtk_paned_set_position(MemorySegment paned, int pos) {
        callPtrInt(GTK_PANED_SET_POSITION, paned, pos);
    }

    static MemorySegment gtk_expander_new_with_label(String label) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) GTK_EXPANDER_NEW_WITH_LABEL.invoke(
                arena.allocateFrom(label == null ? "" : label));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_expander_set_label(MemorySegment exp, String label) {
        try (var arena = Arena.ofConfined()) {
            callPtrPtr(GTK_EXPANDER_SET_LABEL, exp, arena.allocateFrom(label == null ? "" : label));
        }
    }

    static void gtk_expander_set_child(MemorySegment exp, MemorySegment child) {
        callPtrPtr(GTK_EXPANDER_SET_CHILD, exp, child);
    }

    static void gtk_expander_set_expanded(MemorySegment exp, boolean on) {
        try { GTK_EXPANDER_SET_EXPANDED.invoke(exp, on ? 1 : 0); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static boolean gtk_expander_get_expanded(MemorySegment exp) {
        try { return ((int) GTK_EXPANDER_GET_EXPANDED.invoke(exp)) != 0; }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_picture_new() {
        try { return (MemorySegment) GTK_PICTURE_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_picture_set_filename(MemorySegment pic, String filename) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment fn = filename == null ? MemorySegment.NULL : arena.allocateFrom(filename);
            callPtrPtr(GTK_PICTURE_SET_FILENAME, pic, fn);
        }
    }

    static void gtk_picture_set_paintable(MemorySegment picture, MemorySegment paintable) {
        callPtrPtr(GTK_PICTURE_SET_PAINTABLE, picture, paintable);
    }

    /**
     * {@code g_bytes_new} — copies {@code size} bytes from {@code data} into
     * the new GBytes, so the source segment can be released immediately after.
     */
    static MemorySegment g_bytes_new(MemorySegment data, long size) {
        try { return (MemorySegment) G_BYTES_NEW.invoke(data, size); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_bytes_unref(MemorySegment bytes) { callPtr(G_BYTES_UNREF, bytes); }

    /**
     * Decode encoded image bytes (PNG, JPEG, …) into a GdkTexture. Returns
     * {@code MemorySegment.NULL} on decode failure (when {@code error} is
     * also NULL).
     */
    static MemorySegment gdk_texture_new_from_bytes(MemorySegment bytes, MemorySegment errorOut) {
        try { return (MemorySegment) GDK_TEXTURE_NEW_FROM_BYTES.invoke(bytes, errorOut); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_drawing_area_new() {
        try { return (MemorySegment) GTK_DRAWING_AREA_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_drawing_area_set_content_width(MemorySegment a, int w) {
        callPtrInt(GTK_DRAWING_AREA_SET_CONTENT_WIDTH, a, w);
    }

    static void gtk_drawing_area_set_content_height(MemorySegment a, int h) {
        callPtrInt(GTK_DRAWING_AREA_SET_CONTENT_HEIGHT, a, h);
    }

    static void gtk_drawing_area_set_draw_func(MemorySegment a, MemorySegment func,
                                               MemorySegment data, MemorySegment destroy) {
        try { GTK_DRAWING_AREA_SET_DRAW_FUNC.invoke(a, func, data, destroy); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_widget_queue_draw(MemorySegment w) { callPtr(GTK_WIDGET_QUEUE_DRAW, w); }

    /* ---------- GtkFileDialog wrappers ---------- */

    static MemorySegment gtk_file_dialog_new() {
        try { return (MemorySegment) GTK_FILE_DIALOG_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_file_dialog_set_title(MemorySegment dialog, String title) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment t = (title == null || title.isEmpty())
                ? MemorySegment.NULL : arena.allocateFrom(title);
            callPtrPtr(GTK_FILE_DIALOG_SET_TITLE, dialog, t);
        }
    }

    static void gtk_file_dialog_set_initial_folder(MemorySegment dialog, MemorySegment gFile) {
        callPtrPtr(GTK_FILE_DIALOG_SET_INITIAL_FOLDER, dialog, gFile);
    }

    static void gtk_file_dialog_set_initial_name(MemorySegment dialog, String name) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment n = (name == null || name.isEmpty())
                ? MemorySegment.NULL : arena.allocateFrom(name);
            callPtrPtr(GTK_FILE_DIALOG_SET_INITIAL_NAME, dialog, n);
        }
    }

    static void gtk_file_dialog_open(MemorySegment dialog, MemorySegment parent,
                                     MemorySegment cancellable, MemorySegment callback,
                                     MemorySegment userData) {
        try { GTK_FILE_DIALOG_OPEN.invoke(dialog, parent, cancellable, callback, userData); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_file_dialog_open_finish(MemorySegment dialog, MemorySegment result,
                                                     MemorySegment errorOut) {
        try { return (MemorySegment) GTK_FILE_DIALOG_OPEN_FINISH.invoke(dialog, result, errorOut); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_file_dialog_save(MemorySegment dialog, MemorySegment parent,
                                     MemorySegment cancellable, MemorySegment callback,
                                     MemorySegment userData) {
        try { GTK_FILE_DIALOG_SAVE.invoke(dialog, parent, cancellable, callback, userData); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_file_dialog_save_finish(MemorySegment dialog, MemorySegment result,
                                                     MemorySegment errorOut) {
        try { return (MemorySegment) GTK_FILE_DIALOG_SAVE_FINISH.invoke(dialog, result, errorOut); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void gtk_file_dialog_select_folder(MemorySegment dialog, MemorySegment parent,
                                              MemorySegment cancellable, MemorySegment callback,
                                              MemorySegment userData) {
        try { GTK_FILE_DIALOG_SELECT_FOLDER.invoke(dialog, parent, cancellable, callback, userData); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment gtk_file_dialog_select_folder_finish(MemorySegment dialog,
                                                              MemorySegment result,
                                                              MemorySegment errorOut) {
        try { return (MemorySegment) GTK_FILE_DIALOG_SELECT_FOLDER_FINISH.invoke(dialog, result, errorOut); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** {@code g_file_new_for_path} — creates a GFile referring to {@code path}. */
    static MemorySegment g_file_new_for_path(String path) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) G_FILE_NEW_FOR_PATH.invoke(arena.allocateFrom(path));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** {@code g_file_get_path} — caller must {@link #g_free} the returned char*. */
    static MemorySegment g_file_get_path(MemorySegment gFile) {
        try { return (MemorySegment) G_FILE_GET_PATH.invoke(gFile); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /* ---------- GMenu / GAction ---------- */

    static MemorySegment g_menu_new() {
        try { return (MemorySegment) G_MENU_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_menu_append(MemorySegment menu, String label, String detailedAction) {
        try (var arena = Arena.ofConfined()) {
            G_MENU_APPEND.invoke(menu, arena.allocateFrom(label), arena.allocateFrom(detailedAction));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_menu_append_submenu(MemorySegment parent, String label, MemorySegment submenu) {
        try (var arena = Arena.ofConfined()) {
            G_MENU_APPEND_SUBMENU.invoke(parent, arena.allocateFrom(label), submenu);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_menu_append_section(MemorySegment parent, String label, MemorySegment section) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment lbl = label == null ? MemorySegment.NULL : arena.allocateFrom(label);
            G_MENU_APPEND_SECTION.invoke(parent, lbl, section);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_menu_remove_all(MemorySegment menu) { callPtr(G_MENU_REMOVE_ALL, menu); }

    static MemorySegment g_simple_action_group_new() {
        try { return (MemorySegment) G_SIMPLE_ACTION_GROUP_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment g_simple_action_new(String name) {
        try (var arena = Arena.ofConfined()) {
            // parameter_type=NULL → no parameters
            return (MemorySegment) G_SIMPLE_ACTION_NEW.invoke(arena.allocateFrom(name), MemorySegment.NULL);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_simple_action_set_enabled(MemorySegment action, boolean enabled) {
        try { G_SIMPLE_ACTION_SET_ENABLED.invoke(action, enabled ? 1 : 0); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_action_map_add_action(MemorySegment map, MemorySegment action) {
        callPtrPtr(G_ACTION_MAP_ADD_ACTION, map, action);
    }

    /* ---------- helpers ---------- */

    private static void call(MethodHandle mh) {
        try { mh.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
    private static void callPtr(MethodHandle mh, MemorySegment a) {
        try { mh.invoke(a); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
    private static void callPtrPtr(MethodHandle mh, MemorySegment a, MemorySegment b) {
        try { mh.invoke(a, b); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
    private static void callPtrInt(MethodHandle mh, MemorySegment a, int b) {
        try { mh.invoke(a, b); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static String readCString(MemorySegment cstr) {
        if (cstr == null || cstr.address() == 0) return null;
        return cstr.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
