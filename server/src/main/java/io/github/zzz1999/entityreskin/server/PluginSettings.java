package io.github.zzz1999.entityreskin.server;

import io.github.zzz1999.entityreskin.protocol.Identifiers;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Typed view of config.yml. Instances are immutable; reload creates a new instance. */
public final class PluginSettings {

    private static final long MINIMUM_POLL_INTERVAL_SECONDS = 30;
    private static final String DEFAULT_BASE_URL = "http://localhost:8080/";

    private final String baseUrl;
    private final String serverToken;
    private final long pollIntervalSeconds;
    private final List<String> preloadIdentifiers;
    private final PreloadFailurePolicy preloadFailurePolicy;
    private final long preloadTimeoutSeconds;
    private final String preloadKickMessage;
    private final boolean preloadProgressBarEnabled;
    private final String preloadProgressBarTitle;
    private final boolean preloadNotifyText;
    private final String preloadTextMessage;

    /** What to do when a preload appearance fails to download (or is not reported in time) on a client. */
    public enum PreloadFailurePolicy {
        /** Admit the player anyway; the appearance downloads on first sight instead. */
        ALLOW,
        /** Kick the player, naming the appearance that failed. */
        KICK
    }

    private PluginSettings(String baseUrl, String serverToken, long pollIntervalSeconds,
                           List<String> preloadIdentifiers, PreloadFailurePolicy preloadFailurePolicy,
                           long preloadTimeoutSeconds, String preloadKickMessage,
                           boolean preloadProgressBarEnabled, String preloadProgressBarTitle,
                           boolean preloadNotifyText, String preloadTextMessage) {
        this.baseUrl = baseUrl;
        this.serverToken = serverToken;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.preloadIdentifiers = preloadIdentifiers;
        this.preloadFailurePolicy = preloadFailurePolicy;
        this.preloadTimeoutSeconds = preloadTimeoutSeconds;
        this.preloadKickMessage = preloadKickMessage;
        this.preloadProgressBarEnabled = preloadProgressBarEnabled;
        this.preloadProgressBarTitle = preloadProgressBarTitle;
        this.preloadNotifyText = preloadNotifyText;
        this.preloadTextMessage = preloadTextMessage;
    }

    public static PluginSettings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        // Resource backend base URL, read from config.yml; a JVM system property of the same name
        // takes precedence (convenient for development). Defaults to a local backend.
        String baseUrl = System.getProperty("entityreskin.backend.base-url",
                config.getString("backend.base-url", DEFAULT_BASE_URL)).trim();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        String serverToken = config.getString("backend.server-token", "").trim();
        long pollInterval = Math.max(MINIMUM_POLL_INTERVAL_SECONDS,
                config.getLong("backend.poll-interval-seconds", 300));

        List<String> preload = new ArrayList<String>();
        for (String identifier : config.getStringList("preload")) {
            if (Identifiers.isValid(identifier)) {
                preload.add(identifier);
            } else {
                plugin.getLogger().warning("Skipping invalid appearance identifier in config.yml preload list: " + identifier);
            }
        }
        PreloadFailurePolicy policy;
        try {
            policy = PreloadFailurePolicy.valueOf(
                    config.getString("preload-failure.policy", "ALLOW").trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid preload-failure.policy in config.yml; using ALLOW.");
            policy = PreloadFailurePolicy.ALLOW;
        }
        long preloadTimeout = Math.max(0, config.getLong("preload-failure.timeout-seconds", 30));
        String kickMessage = config.getString("preload-failure.kick-message",
                "Failed to download appearance resources: {appearance}. Please retry later or contact an administrator.");

        boolean progressEnabled = config.getBoolean("preload-progress-bar.enabled", false);
        String progressTitle = config.getString("preload-progress-bar.title", "Loading appearance resources…");
        boolean notifyText = config.getBoolean("preload-progress-bar.notify-text", true);
        String textMessage = config.getString("preload-progress-bar.text-message",
                "Preloading appearance resources: {appearances}");

        return new PluginSettings(baseUrl, serverToken, pollInterval,
                Collections.unmodifiableList(preload), policy, preloadTimeout, kickMessage,
                progressEnabled, progressTitle, notifyText, textMessage);
    }

    public boolean isConfigured() {
        return !serverToken.isEmpty();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String serverToken() {
        return serverToken;
    }

    public long pollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public List<String> preloadIdentifiers() {
        return preloadIdentifiers;
    }

    public PreloadFailurePolicy preloadFailurePolicy() {
        return preloadFailurePolicy;
    }

    public long preloadTimeoutSeconds() {
        return preloadTimeoutSeconds;
    }

    public String preloadKickMessage() {
        return preloadKickMessage;
    }

    public boolean preloadProgressBarEnabled() {
        return preloadProgressBarEnabled;
    }

    public String preloadProgressBarTitle() {
        return preloadProgressBarTitle;
    }

    public boolean preloadNotifyText() {
        return preloadNotifyText;
    }

    public String preloadTextMessage() {
        return preloadTextMessage;
    }
}
