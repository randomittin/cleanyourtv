package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FontChoiceTest {
    @Test
    public void outfitIsTheDefaultAndFirst() {
        assertEquals("outfit", FontChoice.IDS.get(0));
        assertEquals("outfit", FontChoice.parse(null));
        assertEquals("outfit", FontChoice.parse(""));
        assertEquals("outfit", FontChoice.parse("   "));
        assertTrue(FontChoice.isOutfit(null));
    }

    @Test
    public void everyKnownIdParsesToItself() {
        for (String id : FontChoice.IDS) assertEquals(id, FontChoice.parse(id));
        assertEquals(6, FontChoice.IDS.size());
    }

    @Test
    public void parsingIgnoresCaseAndSpaces() {
        assertEquals("manrope", FontChoice.parse("  Manrope "));
        assertEquals("plusjakartasans", FontChoice.parse("PLUSJAKARTASANS"));
        assertFalse(FontChoice.isOutfit("inter"));
    }

    @Test
    public void unknownIdsFallBackToOutfit() {
        assertEquals("outfit", FontChoice.parse("comic-sans"));
        assertEquals("outfit", FontChoice.parse("theme"));
        assertEquals("outfit", FontChoice.parse("Inter Tight"));
    }

    @Test
    public void prefKeyIsStable() {
        assertEquals("font", FontChoice.PREF_KEY);
        assertEquals(LookPrefs.FONT, FontChoice.PREF_KEY);
    }

    @Test
    public void outfitResourcesMapToTheirRoles() {
        assertEquals(FontChoice.Role.BODY, FontChoice.roleForOutfitResource(R.font.outfit));
        assertEquals(FontChoice.Role.REGULAR, FontChoice.roleForOutfitResource(R.font.outfit_regular));
        assertEquals(FontChoice.Role.LIGHT, FontChoice.roleForOutfitResource(R.font.outfit_light));
        assertEquals(FontChoice.Role.EXTRALIGHT, FontChoice.roleForOutfitResource(R.font.outfit_extralight));
        assertEquals(FontChoice.Role.MEDIUM, FontChoice.roleForOutfitResource(R.font.outfit_medium));
    }

    @Test
    public void otherResourcesHaveNoRole() {
        assertNull(FontChoice.roleForOutfitResource(0));
        assertNull(FontChoice.roleForOutfitResource(R.font.manrope));
        assertNull(FontChoice.roleForOutfitResource(R.string.font_body));
    }

    @Test
    public void roleWeightsAreOrdered() {
        assertEquals(200, FontChoice.Role.EXTRALIGHT.weight);
        assertEquals(300, FontChoice.Role.LIGHT.weight);
        assertEquals(400, FontChoice.Role.REGULAR.weight);
        assertEquals(400, FontChoice.Role.BODY.weight);
        assertEquals(500, FontChoice.Role.MEDIUM.weight);
    }

    @Test
    public void everyNonOutfitIdHasABundledFont() {
        assertEquals(0, FontChoice.fontResource("outfit"));
        for (String id : FontChoice.IDS) {
            if (!FontChoice.OUTFIT.equals(id)) assertTrue(id, FontChoice.fontResource(id) != 0);
        }
    }

    @Test
    public void onlyTextViewTagsAreIntercepted() {
        assertTrue(FontFactory.isPlainTextViewTag("TextView"));
        assertTrue(FontFactory.isPlainTextViewTag("Button"));
        assertTrue(FontFactory.isPlainTextViewTag("EditText"));
        assertFalse(FontFactory.isPlainTextViewTag("ImageView"));
        assertFalse(FontFactory.isPlainTextViewTag("LinearLayout"));
        assertFalse(FontFactory.isPlainTextViewTag("android.widget.TextView"));
    }
}
