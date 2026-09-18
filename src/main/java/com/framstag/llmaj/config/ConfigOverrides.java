package com.framstag.llmaj.config;

import java.util.List;

/**
 * The command line overrides that were actually supplied by the user.
 * <p>
 * Every component is {@code null} when the corresponding command line option was not passed,
 * which is what makes the configuration precedence order work: an override that is absent must
 * leave the loaded configuration untouched, while an override that is present wins even when its
 * value equals the built-in default.
 * <p>
 * This type deliberately has no dependency on the argument parsing library, so the merge can be
 * unit tested on its own.
 */
public record ConfigOverrides(Boolean logRequests,
                              Boolean logResponses,
                              Boolean executionTrace,
                              Boolean executionTraceSystem,
                              Integer taskParallelism) {

    /**
     * Where the effective value of a setting came from.
     */
    public enum Source {
        COMMAND_LINE,
        CONFIG_FILE,
        DEFAULT
    }

    /**
     * The effective value of one setting together with the source it was taken from.
     */
    public record Resolution(String setting, String value, Source source) {
    }

    /**
     * No command line option was passed.
     */
    public static final ConfigOverrides NONE = new ConfigOverrides(null, null, null, null, null);

    /**
     * Writes the supplied overrides onto the given configuration, leaving every setting whose
     * option was not passed exactly as it was loaded.
     */
    public void applyTo(Config config) {
        if (logRequests != null) {
            config.setLogRequests(logRequests);
        }

        if (logResponses != null) {
            config.setLogResponses(logResponses);
        }

        if (executionTrace != null) {
            config.setExecutionTrace(executionTrace);
        }

        if (executionTraceSystem != null) {
            config.setExecutionTraceSystem(executionTraceSystem);
        }

        if (taskParallelism != null) {
            config.setTaskParallelism(taskParallelism);
        }
    }

    /**
     * The effective value of every overridable setting and the source it came from. Takes the
     * configuration after {@link #applyTo(Config)} has been called.
     */
    public List<Resolution> resolutions(Config config) {
        return List.of(
                resolve("logRequests", logRequests != null,
                        String.valueOf(config.isLogRequests()),
                        config.isProvidedInConfigFile("logRequests")),
                resolve("logResponses", logResponses != null,
                        String.valueOf(config.isLogResponses()),
                        config.isProvidedInConfigFile("logResponses")),
                resolve("executionTrace", executionTrace != null,
                        String.valueOf(config.isExecutionTrace()),
                        config.isProvidedInConfigFile("executionTrace")),
                resolve("executionTraceSystem", executionTraceSystem != null,
                        String.valueOf(config.isExecutionTraceSystem()),
                        config.isProvidedInConfigFile("executionTraceSystem")),
                resolve("taskParallelism", taskParallelism != null,
                        String.valueOf(config.getTaskParallelism()),
                        config.isProvidedInConfigFile("taskParallelism")));
    }

    private static Resolution resolve(String setting,
                                      boolean suppliedOnCommandLine,
                                      String effectiveValue,
                                      boolean providedInConfigFile) {
        Source source;

        if (suppliedOnCommandLine) {
            source = Source.COMMAND_LINE;
        } else if (providedInConfigFile) {
            source = Source.CONFIG_FILE;
        } else {
            source = Source.DEFAULT;
        }

        return new Resolution(setting, effectiveValue, source);
    }
}
