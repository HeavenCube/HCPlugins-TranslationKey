package fr.noltox.hcplugins.translationkey;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MainThreadQueueTest {
    @Test void schedulerRejectionIsReportedAsNotAccepted() {
        var failures = new ArrayList<RuntimeException>();
        var queue = new MainThreadQueue(task -> { throw new IllegalStateException("disabled"); }, failures::add);
        assertFalse(queue.submit(() -> fail()));
        assertFalse(queue.pending());
        assertEquals(1, failures.size());
    }
    @Test void coalescesPreservesOrderAndBatches() {
        var scheduled = new ArrayDeque<Runnable>();
        var received = new ArrayList<Integer>();
        var queue = new MainThreadQueue(scheduled::add, ex -> fail(ex));
        for (int i = 0; i < 130; i++) { int value = i; assertTrue(queue.submit(() -> received.add(value))); }
        assertEquals(1, scheduled.size());
        scheduled.removeFirst().run();
        assertEquals(64, received.size());
        assertEquals(1, scheduled.size());
        while (!scheduled.isEmpty()) scheduled.removeFirst().run();
        assertFalse(queue.pending());
        assertEquals(java.util.stream.IntStream.range(0, 130).boxed().toList(), received);
    }
    @Test void fullQueueAndClosedQueueRejectWithoutRunning() {
        var scheduled = new ArrayDeque<Runnable>();
        var queue = new MainThreadQueue(scheduled::add, ex -> fail(ex));
        for (int i = 0; i < 512; i++) assertTrue(queue.submit(() -> fail("Closed work must not run")));
        assertFalse(queue.submit(() -> fail()));
        queue.close();
        scheduled.removeFirst().run();
        assertFalse(queue.submit(() -> fail()));
        assertFalse(queue.pending());
    }
    @Test void callbackCanEnqueueAndFailureDoesNotLoseFollowingWork() {
        var scheduled = new ArrayDeque<Runnable>();
        var failures = new ArrayList<RuntimeException>();
        var count = new AtomicInteger();
        var queue = new MainThreadQueue(scheduled::add, failures::add);
        queue.submit(() -> { queue.submit(count::incrementAndGet); throw new IllegalArgumentException("expected"); });
        scheduled.removeFirst().run();
        assertEquals(1, count.get()); assertEquals(1, failures.size()); assertFalse(queue.pending());
    }
    @Test void concurrentProducersCannotLoseAcceptedWork() throws Exception {
        var scheduled = new ConcurrentLinkedQueue<Runnable>();
        var queue = new MainThreadQueue(scheduled::add, ex -> fail(ex));
        var accepted = new AtomicInteger(); var received = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 200; i++) tasks.add(executor.submit(() -> {
                if (queue.submit(received::incrementAndGet)) accepted.incrementAndGet();
            }));
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, scheduled.size());
        Runnable next;
        while ((next = scheduled.poll()) != null) next.run();
        assertEquals(accepted.get(), received.get());
        queue.close();
    }
}
