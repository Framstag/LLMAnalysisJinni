package com.framstag.llmaj.lc4j;

import com.framstag.llmaj.display.ProgressCallback;
import dev.langchain4j.exception.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Runs the attempts of one task step and decides which outcome the step ends with.
 * <p>
 * A step is one non-loop task execution or one loop index worker. The attempts are sequential and
 * run on the thread that owns the step, so no pool and no shared state are involved.
 * <p>
 * A response the engine cannot use ({@link TaskStepOutcome#isAccepted()} false) is attempted again
 * until the budget is used. A model error that another attempt may get past
 * ({@link RetriableException}) is turned into such a rejection; every other error, including every
 * {@link IOException}, is left to the caller and ends the step without spending a further attempt,
 * because another attempt cannot repair it.
 */
public class TaskStepRetrier {
    private static final Logger logger = LoggerFactory.getLogger(TaskStepRetrier.class);

    /**
     * Performs one attempt of the step.
     */
    @FunctionalInterface
    public interface StepAttempt {
        /**
         * @param attemptNumber which attempt this is, starting at 1
         * @param repairHint    what the previous attempt is told went wrong, or null for the first
         *                      attempt
         * @return the outcome of this attempt
         * @throws IOException when the engine itself failed, which ends the step
         */
        TaskStepOutcome attempt(int attemptNumber, String repairHint) throws IOException;
    }

    private final int maxAttempts;
    private final ProgressCallback callback;
    private final String taskId;
    private final Integer loopIndex;

    /**
     * @param maxAttempts maximum number of attempts of the step; below 1 it is raised to 1, so a
     *                    step is always attempted at least once
     * @param callback    display callback that is told about every retry
     * @param taskId      task the step belongs to, used for the reporting
     * @param loopIndex   loop index of the step, or null for a non-loop task
     */
    public TaskStepRetrier(int maxAttempts,
                           ProgressCallback callback,
                           String taskId,
                           Integer loopIndex) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.callback = callback == null ? ProgressCallback.noOp() : callback;
        this.taskId = taskId;
        this.loopIndex = loopIndex;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Attempts the step until it produces an accepted outcome or its attempts are used.
     *
     * @return the accepted outcome, or the failure of the last attempt
     * @throws IOException when an attempt failed for a reason another attempt cannot repair
     */
    public TaskStepOutcome run(StepAttempt step) throws IOException {
        TaskStepFailure lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            TaskStepOutcome outcome;

            try {
                outcome = step.attempt(attempt, lastFailure == null ? null : lastFailure.repairHint());
            } catch (RetriableException e) {
                outcome = TaskStepOutcome.rejected(TaskStepFailure.of(
                        StepFailureReason.RETRIABLE_MODEL_ERROR,
                        e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
            }

            if (outcome.isAccepted()) {
                if (attempt > 1) {
                    logger.info("Task '{}'{} produced an accepted answer on attempt {}/{}",
                            taskId, stepLabel(), attempt, maxAttempts);
                }

                callback.onComplete(taskId, loopIndex);

                return outcome;
            }

            lastFailure = outcome.failure();

            if (attempt < maxAttempts) {
                logger.warn("Task '{}'{} attempt {}/{} failed ({}), retrying",
                        taskId, stepLabel(), attempt, maxAttempts, lastFailure.displayMessage());
                callback.onRetry(taskId, loopIndex, attempt, maxAttempts, lastFailure.displayMessage());
            } else {
                logger.warn("Task '{}'{} failed after {} attempt(s) ({})",
                        taskId, stepLabel(), maxAttempts, lastFailure.displayMessage());
            }
        }

        return TaskStepOutcome.rejected(lastFailure);
    }

    private String stepLabel() {
        return loopIndex != null ? " [index " + loopIndex + "]" : "";
    }
}
