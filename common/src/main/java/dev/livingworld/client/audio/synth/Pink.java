package dev.livingworld.client.audio.synth;

/** Pink (1/f) noise shaper (Paul Kellett's economy filter). Output RMS roughly equals the white input RMS. */
final class Pink {
    private float b0, b1, b2;

    float next(float white) {
        b0 = 0.99765f * b0 + white * 0.0990460f;
        b1 = 0.96300f * b1 + white * 0.2965164f;
        b2 = 0.57000f * b2 + white * 1.0526913f;
        return (b0 + b1 + b2 + white * 0.1848f) * 0.335f;
    }
}
