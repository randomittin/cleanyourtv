package io.github.randomittin.voiceassistant;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Makes the app present as the connected brain: the launcher tile (and its card) is one of three
 * activity-aliases of {@link AssistActivity}, and exactly one is enabled at a time. AssistActivity
 * itself stays the stable target for ASSIST, VOICE_COMMAND, WEB_SEARCH and SEARCH_LONG_PRESS.
 */
public final class BrainIdentity {

    public enum Identity {
        DEFAULT(".LauncherDefault"), CLAUDE(".LauncherClaude"), CHATGPT(".LauncherChatGpt");

        final String aliasName;

        Identity(String aliasName) {
            this.aliasName = aliasName;
        }
    }

    private BrainIdentity() {
    }

    /** Claude when it is selected and its key is set, ChatGPT likewise, otherwise the neutral name. */
    public static Identity select(String brain, boolean hasAnthropicKey, boolean hasOpenAiKey) {
        if ("claude".equals(brain) && hasAnthropicKey) {
            return Identity.CLAUDE;
        }
        if ("chatgpt".equals(brain) && hasOpenAiKey) {
            return Identity.CHATGPT;
        }
        return Identity.DEFAULT;
    }

    /** Enables the alias for the current brain and keys and disables the other two. Safe to repeat. */
    static void apply(Context context) {
        Settings settings = new Settings(context);
        Identity wanted = select(settings.brain(), settings.hasAnthropicKey(), settings.hasOpenAiKey());
        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();
        // Enable first, then disable the rest, so there is never a moment with no launcher entry.
        pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + wanted.aliasName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (Identity other : Identity.values()) {
            if (other != wanted) {
                pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + other.aliasName),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
    }
}
