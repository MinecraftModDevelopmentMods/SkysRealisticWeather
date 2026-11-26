package zone.moddev.mc.skysrealisticweather.admin;

import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;

final class SeasonClock {
    private static final int DAY = ClimateProfile.TICKS_PER_DAY;

    private SeasonClock() {
    }

    static long phase(long now, ClimateProfile profile, Season season, int day) {
        long year = (long) profile.daysInYear() * DAY;
        long start = Math.subtractExact(now, Math.floorMod(now, year));
        return Math.addExact(start, phaseOffset(profile, season, day) + Math.floorMod(now, DAY));
    }

    static long step(long now, ClimateProfile profile, boolean forward) {
        Season current = profile.at(now).calendar.season;
        Season target = Season.values()[(current.ordinal() + (forward ? 1 : 3)) % ClimateProfile.SEASON_COUNT];
        long tick = phase(now, profile, target, 1);
        long year = (long) profile.daysInYear() * DAY;
        if (forward && target == Season.SPRING) return Math.addExact(tick, year);
        if (!forward && current == Season.SPRING) return Math.subtractExact(tick, year);
        return tick;
    }

    static long advance(long now, int days) {
        return Math.addExact(now, (long) days * DAY);
    }

    static long timeOfDay(long now, int tick) {
        return Math.addExact(Math.subtractExact(now, Math.floorMod(now, DAY)), tick);
    }

    private static long phaseOffset(ClimateProfile profile, Season season, int day) {
        int offset = day - 1;
        for (int index = 0; index < season.ordinal(); index++) offset += profile.period(index).days;
        return (long) offset * DAY;
    }
}
