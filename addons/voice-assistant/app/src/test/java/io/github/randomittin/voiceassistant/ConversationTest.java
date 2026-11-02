package io.github.randomittin.voiceassistant;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class ConversationTest {

    @Test
    public void keepsOnlyTheLastSixTurnsAndStartsOnUser() {
        Conversation c = new Conversation();
        for (int i = 1; i <= 8; i++) {
            c.addUser("q" + i);
            c.addAssistant("a" + i);
        }
        List<Conversation.Message> m = c.messages();
        assertEquals(12, m.size());
        assertEquals("user", m.get(0).role);
        assertEquals("q3", m.get(0).text);
        assertEquals("a8", m.get(11).text);
    }

    @Test
    public void unansweredQuestionIsDroppedSoRolesAlternate() {
        Conversation c = new Conversation();
        c.addUser("q1");
        c.addAssistant("a1");
        c.addUser("q2");
        c.dropTrailingUser();
        List<Conversation.Message> m = c.messages();
        assertEquals(2, m.size());
        assertEquals("assistant", m.get(1).role);
        c.dropTrailingUser();
        assertEquals(2, c.size());
    }

    @Test
    public void trimmingNeverLeavesALeadingAssistant() {
        Conversation c = new Conversation();
        for (int i = 0; i < 6; i++) {
            c.addUser("q" + i);
            c.addAssistant("a" + i);
        }
        c.addUser("extra");
        List<Conversation.Message> m = c.messages();
        assertEquals("user", m.get(0).role);
        assertEquals("extra", m.get(m.size() - 1).text);
    }
}
