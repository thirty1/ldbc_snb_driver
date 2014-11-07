package com.ldbc.driver.runtime.scheduling;

import com.ldbc.driver.Operation;
import com.ldbc.driver.runtime.ConcurrentErrorReporter;
import com.ldbc.driver.runtime.coordination.CompletionTimeException;
import com.ldbc.driver.runtime.coordination.GlobalCompletionTimeReader;
import com.ldbc.driver.temporal.TemporalUtil;

public class GctDependencyCheck implements SpinnerCheck {
    private static final TemporalUtil TEMPORAL_UTIL = new TemporalUtil();
    private final GlobalCompletionTimeReader globalCompletionTimeReader;
    private final Operation<?> operation;
    private final ConcurrentErrorReporter errorReporter;

    public GctDependencyCheck(GlobalCompletionTimeReader globalCompletionTimeReader,
                              Operation<?> operation,
                              ConcurrentErrorReporter errorReporter) {
        this.globalCompletionTimeReader = globalCompletionTimeReader;
        this.operation = operation;
        this.errorReporter = errorReporter;
    }

    @Override
    public SpinnerCheckResult doCheck() {
        try {
            return (globalCompletionTimeReader.globalCompletionTimeAsMilli() >= operation.dependencyTimeStamp()) ? SpinnerCheckResult.PASSED : SpinnerCheckResult.STILL_CHECKING;
        } catch (CompletionTimeException e) {
            errorReporter.reportError(this,
                    String.format(
                            "Error encountered while reading GCT for query %s\n%s",
                            operation.getClass().getSimpleName(),
                            ConcurrentErrorReporter.stackTraceToString(e)));
            return SpinnerCheckResult.FAILED;
        }
    }

    @Override
    public boolean handleFailedCheck(Operation<?> operation) {
        try {
            // Note, GCT printed here may be a little later than GCT that was measured during check
            errorReporter.reportError(this,
                    String.format("GCT(%s) has not advanced sufficiently to execute operation\n"
                                    + "Operation: %s\n"
                                    + "Time Stamp: %s\n"
                                    + "Dependency Time Stamp: %s",
                            TEMPORAL_UTIL.millisecondsToDateTimeString(globalCompletionTimeReader.globalCompletionTimeAsMilli()),
                            operation.toString(),
                            operation.timeStamp(),
                            operation.dependencyTimeStamp()));
            return false;
        } catch (CompletionTimeException e) {
            errorReporter.reportError(this,
                    String.format(
                            "Error encountered in handleFailedCheck while reading GCT for query %s\n%s",
                            operation.getClass().getSimpleName(),
                            ConcurrentErrorReporter.stackTraceToString(e)));
            return false;
        }
    }
}
