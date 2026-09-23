package com.example.testctl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hand-off mechanism itself: a submitted task only runs once something calls drain() (the
 * stand-in for Game's main loop), submitAndWait returns whatever that task produced, a task
 * that never gets drained in time times out rather than hanging forever, and multiple tasks
 * drain in the order they were submitted.
 */
class GameCommandQueueTest {

    @Test
    void submittedTaskDoesNotRunUntilDrained() {
        GameCommandQueue queue = new GameCommandQueue();
        List<String> ran = new ArrayList<>();

        Thread submitter = new Thread(() -> {
            try {
                queue.submitAndWait(() -> {
                    ran.add("ran");
                    return "done";
                }, 2000);
            } catch (TimeoutException e) {
                fail("should not time out");
            }
        });
        submitter.start();

        // Give the submitter thread a moment to enqueue - it must still be blocked on the
        // future, since nothing has drained the queue yet.
        awaitQueueNonEmptyOrThread(submitter, ran);
        assertTrue(ran.isEmpty(), "task must not run before drain() is called");

        queue.drain();
        joinQuietly(submitter);
        assertEquals(List.of("ran"), ran);
    }

    @Test
    void submitAndWaitReturnsTheTasksResult() throws Exception {
        GameCommandQueue queue = new GameCommandQueue();
        Thread drainer = new Thread(() -> {
            sleepQuietly(50);
            queue.drain();
        });
        drainer.start();

        String result = queue.submitAndWait(() -> "hello", 2000);
        assertEquals("hello", result);
        joinQuietly(drainer);
    }

    @Test
    void submitAndWaitTimesOutIfNeverDrained() {
        GameCommandQueue queue = new GameCommandQueue();
        assertThrows(TimeoutException.class, () -> queue.submitAndWait(() -> "never", 100));
    }

    @Test
    void multipleTasksDrainInSubmissionOrder() {
        GameCommandQueue queue = new GameCommandQueue();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int n = i;
            // Enqueue directly via a Runnable-returning task, ignoring the future - drain()
            // itself is what's under test here, not the cross-thread handoff.
            new Thread(() -> {
                try {
                    queue.submitAndWait(() -> {
                        order.add(n);
                        return null;
                    }, 2000);
                } catch (TimeoutException ignored) {
                }
            }).start();
            sleepQuietly(20); // keep submission order deterministic for this assertion
        }
        sleepQuietly(50);
        queue.drain();
        assertEquals(List.of(0, 1, 2, 3, 4), order);
    }

    private void awaitQueueNonEmptyOrThread(Thread t, List<String> ran) {
        for (int i = 0; i < 50 && ran.isEmpty(); i++) sleepQuietly(10);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void joinQuietly(Thread t) {
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
