package io.github.randomittin.voiceassistant;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BrainIdentityTest {

    @Test
    public void claudeNeedsBothSelectionAndItsKey() {
        assertEquals(BrainIdentity.Identity.CLAUDE, BrainIdentity.select("claude", true, false));
        assertEquals(BrainIdentity.Identity.CLAUDE, BrainIdentity.select("claude", true, true));
        assertEquals(BrainIdentity.Identity.DEFAULT, BrainIdentity.select("claude", false, true));
    }

    @Test
    public void chatGptNeedsBothSelectionAndItsKey() {
        assertEquals(BrainIdentity.Identity.CHATGPT, BrainIdentity.select("chatgpt", false, true));
        assertEquals(BrainIdentity.Identity.CHATGPT, BrainIdentity.select("chatgpt", true, true));
        assertEquals(BrainIdentity.Identity.DEFAULT, BrainIdentity.select("chatgpt", true, false));
    }

    @Test
    public void noKeysOrUnknownBrainIsTheNeutralName() {
        assertEquals(BrainIdentity.Identity.DEFAULT, BrainIdentity.select("claude", false, false));
        assertEquals(BrainIdentity.Identity.DEFAULT, BrainIdentity.select("chatgpt", false, false));
        assertEquals(BrainIdentity.Identity.DEFAULT, BrainIdentity.select("other", true, true));
        assertEquals(BrainIdentity.Identity.DEFAULT, BrainIdentity.select(null, true, true));
    }

    @Test
    public void everyIdentityHasItsOwnAliasName() {
        assertEquals(".LauncherDefault", BrainIdentity.Identity.DEFAULT.aliasName);
        assertEquals(".LauncherClaude", BrainIdentity.Identity.CLAUDE.aliasName);
        assertEquals(".LauncherChatGpt", BrainIdentity.Identity.CHATGPT.aliasName);
    }
}
