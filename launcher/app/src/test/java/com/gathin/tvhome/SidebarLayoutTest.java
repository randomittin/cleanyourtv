package com.gathin.tvhome;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SidebarLayoutTest {
    private static ForkKeys layout(String name) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("layout", name);
        return ForkKeys.from(merged);
    }

    @Test public void activeOnlyForSidebarPick() {
        assertTrue(SidebarLayout.active(layout("sidebar")));
        assertFalse(SidebarLayout.active(layout("dock")));
        assertFalse(SidebarLayout.active(ForkKeys.from(new HashMap<>())));
    }

    @Test public void homeAndSettingsAlwaysShow() {
        assertEquals(Arrays.asList(SidebarLayout.HOME, SidebarLayout.SETTINGS), SidebarLayout.items(false, false, false));
    }

    @Test public void allItemsInOrder() {
        assertEquals(Arrays.asList(SidebarLayout.HOME, SidebarLayout.INPUTS, SidebarLayout.LOOK, SidebarLayout.ASSISTANT,
                SidebarLayout.SETTINGS), SidebarLayout.items(true, true, true));
    }

    @Test public void assistantHiddenWhenNotInstalled() {
        assertFalse(SidebarLayout.items(true, true, false).contains(SidebarLayout.ASSISTANT));
    }

    @Test public void insetIsEdgeMarginPlusRailPlusGap() {
        assertEquals(120, SidebarLayout.insetPx(1f));
        assertEquals(240, SidebarLayout.insetPx(2f));
        assertEquals(132, SidebarLayout.insetPx(1.1f));
        assertEquals(88, SidebarLayout.railWidthPx(1f));
    }

    @Test public void railMoveClampsWithoutWrapping() {
        assertEquals(0, SidebarLayout.move(0, 3, KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(2, SidebarLayout.move(2, 3, KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(1, SidebarLayout.move(0, 3, KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(1, SidebarLayout.move(2, 3, KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(0, SidebarLayout.move(5, 0, KeyEvent.KEYCODE_DPAD_UP));
    }

    @Test public void leftFromFirstColumnEntersRail() {
        assertTrue(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_LEFT, true, 0, 8, 4));
        assertFalse(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_LEFT, true, 1, 9, 4));
    }

    @Test public void leftInSingleRowOnlyFromFirstTile() {
        assertTrue(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_LEFT, false, 0, 0, 1));
        assertFalse(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_LEFT, false, 0, 3, 1));
    }

    @Test public void upEntersRailOnlyFromTopRow() {
        assertTrue(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_UP, true, 2, 2, 4));
        assertFalse(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_UP, true, 2, 6, 4));
        assertTrue(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_UP, false, 0, 5, 1));
    }

    @Test public void rightAndDownNeverEnterRail() {
        assertFalse(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_RIGHT, true, 0, 0, 4));
        assertFalse(SidebarLayout.gridKeyEntersRail(KeyEvent.KEYCODE_DPAD_DOWN, true, 0, 0, 4));
    }
}
