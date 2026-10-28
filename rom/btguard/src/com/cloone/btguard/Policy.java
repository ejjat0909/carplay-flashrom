package com.cloone.btguard;

/**
 * Decides when Bluetooth should be off. Pure logic (no Android types) so it can be tested on a laptop.
 *
 * Wireless Android Auto needs Bluetooth only to start. While the phone is also connected over Bluetooth
 * it sends its music over Bluetooth audio, and a map voice prompt then pauses the music instead of
 * lowering it. Over Wi-Fi Direct alone the music is only ducked. So: once a phone has been in the
 * unit's Wi-Fi Direct group for {@code offAfterMs}, switch Bluetooth off; once the group has been empty
 * for {@code onAfterMs}, switch it back on so the next trip can start. Both delays absorb the
 * reconnect flapping that happens while a session starts or ends.
 */
final class Policy {

    enum Action { NONE, BLUETOOTH_OFF, BLUETOOTH_ON }

    private final long offAfterMs;
    private final long onAfterMs;

    private boolean active;      // is a phone in the group right now
    private long since = -1;     // when 'active' last changed
    private boolean acted;       // already acted for the current stretch

    Policy(long offAfterMs, long onAfterMs) {
        this.offAfterMs = offAfterMs;
        this.onAfterMs = onAfterMs;
    }

    /**
     * @param phoneInGroup a client is in the unit's Wi-Fi Direct group
     * @param now          monotonic milliseconds
     * @return the one action to take now, if any. Each stretch acts at most once, so a person who
     *         switches Bluetooth on by hand during a session is not fought.
     */
    Action update(boolean phoneInGroup, long now) {
        if (since < 0 || phoneInGroup != active) {
            active = phoneInGroup;
            since = now;
            acted = false;
            return Action.NONE;
        }
        if (acted) {
            return Action.NONE;
        }
        long held = now - since;
        if (active && held >= offAfterMs) {
            acted = true;
            return Action.BLUETOOTH_OFF;
        }
        if (!active && held >= onAfterMs) {
            acted = true;
            return Action.BLUETOOTH_ON;
        }
        return Action.NONE;
    }
}
