package com.thrive.pleos.ui.driving

import org.junit.Assert.*
import org.junit.Test

class MotionPolicyTest {
    @Test fun motionRequiresParkAndRespectsInputAndAccessibility() {
        fun mode(parked: Boolean = true, driving: Boolean = false, keyboard: Boolean = false,
            enabled: Boolean = true, reduced: Boolean = false) = motionMode(parked, driving, keyboard, enabled, reduced)
        assertEquals(MotionMode.Full, mode())
        assertEquals(MotionMode.Reduced, mode(reduced = true))
        assertEquals(100, MotionMode.Reduced.duration(220))
        assertEquals(0, MotionMode.Off.duration(220))
        assertEquals(MotionMode.Off, mode(parked = false))
        assertEquals(MotionMode.Off, mode(driving = true))
        assertEquals(MotionMode.Off, mode(keyboard = true))
        assertEquals(MotionMode.Off, mode(enabled = false))
        assertEquals(MotionMode.Off, mode(driving = true, reduced = true))
    }

    @Test fun closingAnOldDialogDoesNotDismissItsReplacementAndActionsRunOnceImmediately() {
        val state = ThriveDialogState()
        val oldOwner = Any(); val newOwner = Any()
        var confirmed = 0; var dismissed = 0
        fun content(title: String) = DialogContent(title, "text", "취소", "확인", { dismissed++ }, { confirmed++ })
        state.show(oldOwner, content("old"))
        state.show(newOwner, content("new"))
        state.close(oldOwner)
        assertTrue(state.visible)
        assertEquals("new", state.content?.title)
        state.confirm()
        assertFalse(state.visible)
        assertEquals(1, confirmed)
        state.confirm(); state.dismiss()
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }
}
