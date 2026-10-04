package fr.noltox.hcplugins.translationkey;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/** Bounded, event-driven handoff. No executor, polling, player capture or wait on the network thread. */
final class MainThreadQueue implements AutoCloseable {
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final Consumer<Runnable> scheduler;
    private final Consumer<RuntimeException> failures;
    private boolean active = true, scheduled;
    MainThreadQueue(Consumer<Runnable> scheduler, Consumer<RuntimeException> failures) {
        this.scheduler = scheduler; this.failures = failures;
    }
    synchronized boolean pending() { return scheduled; }
    boolean submit(Runnable action) {
        boolean start;
        synchronized (this) {
            if (!active || queue.size() >= 512) return false;
            queue.addLast(action); start = !scheduled; scheduled = true;
        }
        return !start || schedule();
    }
    private boolean schedule() {
        try { scheduler.accept(this::drain); return true; }
        catch (RuntimeException ex) { close(); failures.accept(ex); return false; }
    }
    private void drain() {
        for (int i = 0; i < 64; i++) {
            Runnable action;
            synchronized (this) {
                action = active ? queue.pollFirst() : null;
                if (action == null) { scheduled = false; return; }
            }
            try { action.run(); } catch (RuntimeException ex) { failures.accept(ex); }
        }
        boolean again;
        synchronized (this) { again = active && !queue.isEmpty(); if (!again) scheduled = false; }
        if (again) schedule();
    }
    @Override public synchronized void close() { active = false; scheduled = false; queue.clear(); }
}
