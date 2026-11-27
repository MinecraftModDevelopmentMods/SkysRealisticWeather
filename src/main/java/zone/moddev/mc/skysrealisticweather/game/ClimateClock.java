package zone.moddev.mc.skysrealisticweather.game;

/** Calendar offsets never alter the daylight clock or the episode API's clock. */
public final class ClimateClock {
    public static final long MAX_OFFSET = 3650000L * 24000;
    private ClimateClock() { }
    public static long shift(long daylight, long offset) {
        try { return Math.addExact(daylight, offset); }
        catch (ArithmeticException overflow) { return daylight; }
    }
    public static long checked(long offset) {
        if (offset < -MAX_OFFSET || offset > MAX_OFFSET || offset % 24000 != 0)
            throw new IllegalArgumentException("Calendar offset must be whole days within 10,000 years");
        return offset;
    }
}
