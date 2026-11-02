package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HeroPillTest {
    @Test public void pillIsCentredOnTheTileWhenThereIsRoom() {
        assertEquals(450f, HeroPill.clampX(500f, 100f, 1920f, 24f), 0f);
    }

    @Test public void pillStaysInsideTheLeftMargin() {
        assertEquals(24f, HeroPill.clampX(40f, 200f, 1920f, 24f), 0f);
    }

    @Test public void pillStaysInsideTheRightMargin() {
        assertEquals(1920f - 24f - 200f, HeroPill.clampX(1900f, 200f, 1920f, 24f), 0f);
    }

    @Test public void pillWiderThanTheScreenStartsAtTheMargin() {
        assertEquals(24f, HeroPill.clampX(960f, 2000f, 1920f, 24f), 0f);
    }

    @Test public void onlyPillSwitchesTheStyleOn() {
        assertEquals("pill", HeroPill.style("pill"));
        assertEquals("pill", HeroPill.style("  Pill "));
        assertEquals("text", HeroPill.style(""));
        assertEquals("text", HeroPill.style("text"));
        assertEquals("text", HeroPill.style("large"));
        assertEquals("text", HeroPill.style("pil"));
        assertEquals("text", HeroPill.style(null));
    }
}
