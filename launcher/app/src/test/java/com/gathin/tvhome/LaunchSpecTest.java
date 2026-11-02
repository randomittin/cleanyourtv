package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LaunchSpecTest {
    @Test public void packageIsReadFromAnIntentUri() {
        assertEquals("com.netflix.ninja", LaunchSpec.packageOf("intent://title/1#Intent;scheme=nflx;package=com.netflix.ninja;end"));
        assertEquals("com.x", LaunchSpec.packageOf("intent:#Intent;action=android.intent.action.VIEW;package=com.x"));
    }

    @Test public void noPackageForPlainLinksOrJunk() {
        assertNull(LaunchSpec.packageOf("https://www.netflix.com/title/1"));
        assertNull(LaunchSpec.packageOf("intent:#Intent;action=a;end"));
        assertNull(LaunchSpec.packageOf("intent:#Intent;package=;end"));
        assertNull(LaunchSpec.packageOf(null));
    }

    @Test public void launchableTextIsAnIntentUriOrAbsoluteLink() {
        assertTrue(LaunchSpec.looksLaunchable("intent:#Intent;end"));
        assertTrue(LaunchSpec.looksLaunchable(" https://x.example/a "));
        assertTrue(LaunchSpec.looksLaunchable("myapp://play/1"));
        assertFalse(LaunchSpec.looksLaunchable("not a uri"));
        assertFalse(LaunchSpec.looksLaunchable(null));
    }
}
