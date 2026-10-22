package com.framstag.llmaj.lc4j;

import com.framstag.llmaj.tasks.StepTokenUsage;

/**
 * A model call that failed, carrying the token usage of the calls that succeeded before it.
 * <p>
 * A step that asks the model for something the model cannot answer, for example a request that is
 * larger than the context window of the model, spends the tokens of the calls that came before it.
 * Those tokens are spent, so they are carried out of the failed call and recorded for the step.
 * <p>
 * The cause of the failure is kept, so the reason the model gave for stopping it and whether
 * another attempt may get past it are still available.
 */
public class ModelCallFailure extends RuntimeException {

    private final StepTokenUsage tokenUsage;

    /**
     * @param message    what went wrong
     * @param cause      the error the model reported
     * @param tokenUsage the usage of the calls that succeeded before the failure, may be null
     */
    public ModelCallFailure(String message, Throwable cause, StepTokenUsage tokenUsage) {
        super(message, cause);
        this.tokenUsage = tokenUsage;
    }

    /**
     * @return the usage of the calls that succeeded before the failure, may be null
     */
    public StepTokenUsage tokenUsage() {
        return tokenUsage;
    }
}
