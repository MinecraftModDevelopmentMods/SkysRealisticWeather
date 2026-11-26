package zone.moddev.mc.skysrealisticweather.api;

/** An immutable reading of the world calendar. Progress is in [0, 1). */
public final class SeasonSnapshot {
    public final Season season;
    public final int dayOfSeason;
    public final int lengthInDays;
    public final double progress;

    public SeasonSnapshot(Season season, int dayOfSeason, int lengthInDays, double progress) {
        this.season = season;
        this.dayOfSeason = dayOfSeason;
        this.lengthInDays = lengthInDays;
        this.progress = progress;
    }
}
