package dev.livingworld.client.audio.synth;

/**
 * Tiny preallocated event ring. Producers (any thread) take a very short lock; the audio thread checks a volatile
 * counter first, so in the common "nothing queued" case it never touches the lock at all.
 */
final class EventQueue {
    private final int cap;
    private final int[] type;
    private final float[] a, b, c;
    private int head, size;
    private volatile int pending;

    /** Payload of the event returned by the last successful {@link #poll()} (audio thread only). */
    float outA, outB, outC;

    EventQueue(int capacity) {
        cap = capacity;
        type = new int[capacity];
        a = new float[capacity];
        b = new float[capacity];
        c = new float[capacity];
    }

    synchronized void offer(int t, float x, float y, float z) {
        if (size == cap) {
            head = (head + 1) % cap; // full: drop the oldest
            size--;
        }
        int i = (head + size) % cap;
        type[i] = t;
        a[i] = x;
        b[i] = y;
        c[i] = z;
        size++;
        pending = size;
    }

    /** @return the event type, or -1 when the queue is empty. */
    int poll() {
        if (pending == 0) {
            return -1;
        }
        synchronized (this) {
            if (size == 0) {
                pending = 0;
                return -1;
            }
            int i = head;
            head = (head + 1) % cap;
            size--;
            pending = size;
            outA = a[i];
            outB = b[i];
            outC = c[i];
            return type[i];
        }
    }
}
