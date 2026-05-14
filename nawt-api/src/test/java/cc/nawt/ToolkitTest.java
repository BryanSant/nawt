package cc.nawt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolkitTest {

    @Test
    void tryCurrentReturnsNullBeforeLaunch() {
        assertNull(Toolkit.tryCurrent(),
            "Toolkit.tryCurrent() should return null when no backend has been launched");
    }

    @Test
    void requireLaunchedThrowsWithGuidance() {
        IllegalStateException e = assertThrows(IllegalStateException.class, Toolkit::requireLaunched);
        assertTrue(e.getMessage().contains("Toolkit.launch"),
            "Error message should point users at Toolkit.launch(...): " + e.getMessage());
    }

    @Test
    void detectThrowsWhenNoBackendsAvailable() {
        // The api module's tests run without any backend on the module path,
        // so Toolkit.detect() must surface a clean failure rather than NPE/etc.
        IllegalStateException e = assertThrows(IllegalStateException.class, Toolkit::detect);
        String msg = e.getMessage();
        assertTrue(msg != null && (msg.contains("backend") || msg.contains("module path")),
            "Error message should explain that no backend is available: " + msg);
    }

    @Test
    void uiIsUiThreadFalseBeforeLaunch() {
        assertFalse(Ui.isUiThread(),
            "Ui.isUiThread() must return false (not throw) before launch");
    }
}
