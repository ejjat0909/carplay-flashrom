package com.cloone.btguard;

public class PolicyTest {
    static int failures;
    static void eq(String what, Object want, Object got) {
        if (!want.equals(got)) { failures++; System.out.println("FAIL " + what + ": want " + want + " got " + got); }
        else System.out.println("ok   " + what);
    }
    public static void main(String[] a) {
        Policy.Action NONE = Policy.Action.NONE, OFF = Policy.Action.BLUETOOTH_OFF, ON = Policy.Action.BLUETOOTH_ON;
        Policy p = new Policy(8000, 15000);
        eq("first sample does nothing", NONE, p.update(false, 0));
        eq("empty group for 14 s: not yet", NONE, p.update(false, 14000));
        eq("empty for 15 s: Bluetooth ON", ON, p.update(false, 15000));
        eq("acts once per stretch", NONE, p.update(false, 40000));
        eq("phone joins: wait", NONE, p.update(true, 41000));
        eq("7 s in: not yet", NONE, p.update(true, 48000));
        eq("8 s in: Bluetooth OFF", OFF, p.update(true, 49000));
        eq("user turns BT on by hand: not fought", NONE, p.update(true, 80000));
        eq("phone leaves: wait", NONE, p.update(false, 90000));
        eq("blip back in (reconnect flap) resets the timer", NONE, p.update(true, 95000));
        eq("blip out again", NONE, p.update(false, 96000));
        eq("14 s empty", NONE, p.update(false, 110000));
        eq("15 s empty: Bluetooth ON", ON, p.update(false, 111000));
        Policy q = new Policy(8000, 15000);
        q.update(true, 0);
        eq("join then leave before 8 s: nothing", NONE, q.update(true, 5000));
        eq("left", NONE, q.update(false, 6000));
        eq("still nothing 10 s later", NONE, q.update(false, 16000));
        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
