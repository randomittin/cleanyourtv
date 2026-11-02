package io.github.randomittin.voiceassistant;

import java.util.ArrayList;
import java.util.List;

/** The last few question/answer turns, kept only while the assistant is open. Pure logic. */
public final class Conversation {

    public static final int MAX_TURNS = 6;

    public static final class Message {
        public final String role;
        public final String text;

        Message(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private final List<Message> messages = new ArrayList<>();

    public void addUser(String text) {
        messages.add(new Message("user", text));
        trim();
    }

    public void addAssistant(String text) {
        messages.add(new Message("assistant", text));
        trim();
    }

    /** Removes a question that got no answer, so roles keep alternating for the next request. */
    public void dropTrailingUser() {
        if (!messages.isEmpty() && "user".equals(messages.get(messages.size() - 1).role)) {
            messages.remove(messages.size() - 1);
        }
    }

    public List<Message> messages() {
        return new ArrayList<>(messages);
    }

    public int size() {
        return messages.size();
    }

    public void clear() {
        messages.clear();
    }

    /** Keeps the newest MAX_TURNS pairs and always starts on a user message (the APIs require it). */
    private void trim() {
        int max = MAX_TURNS * 2;
        while (messages.size() > max) {
            messages.remove(0);
        }
        while (!messages.isEmpty() && !"user".equals(messages.get(0).role)) {
            messages.remove(0);
        }
    }
}
