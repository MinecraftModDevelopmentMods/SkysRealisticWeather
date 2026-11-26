package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.world.storage.WorldInfo;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.live.Observation;

/** Gives vanilla commands and sleep precedence over the live feed and climate schedule. */
final class LiveWeatherControl {
    private final WorldInfo info;
    private Timers last;
    private long heldUntil;
    private Observation heldObservation;
    private Observation applied;
    private WeatherEpisode episode;
    private WeatherEpisode heldEpisode;
    private boolean scheduled;

    LiveWeatherControl(WorldInfo info) {
        this.info = info;
    }

    Observation beforeTick(long tick, Observation available) {
        return beforeTick(tick, available, false, null);
    }

    Observation beforeTick(long tick, Observation available, boolean schedule, WeatherEpisode candidate) {
        if (last != null && !last.matches(info)) {
            int duration = last.changedDuration(info);
            if (duration > 0) {
                heldUntil = tick + duration;
            } else {
                heldObservation = available;
                heldEpisode = candidate;
            }
            applied = null;
            scheduled = false;
        } else if (last == null && schedule) {
            int duration = Math.max(info.getCleanWeatherTime(), Math.max(info.getRainTime(), info.getThunderTime()));
            if (duration > 2) heldUntil = tick + duration;
        }
        if (heldObservation != null && heldObservation != available) heldObservation = null;
        if (heldEpisode != null && (candidate == null || heldEpisode.startTick != candidate.startTick)) heldEpisode = null;
        boolean useLive = available != null && heldObservation == null && tick >= heldUntil;
        boolean useSchedule = available == null && schedule && tick >= heldUntil;
        if (!useLive && !useSchedule) {
            if ((applied != null || scheduled) && last != null && last.matches(info)) {
                // Release the short timers and let vanilla pick a fresh interval.
                info.setRainTime(0);
                info.setThunderTime(0);
            }
            applied = null;
            episode = null;
            scheduled = false;
            return null;
        }

        episode = useSchedule && heldEpisode == null ? candidate : null;
        info.setRaining(useLive ? available.condition.wet() : episode != null && episode.kind.wet);
        info.setThundering(useLive ? available.condition == Observation.Condition.THUNDER
                : episode != null && episode.kind.thunder);
        info.setCleanWeatherTime(0);
        info.setRainTime(2);
        info.setThunderTime(2);
        applied = useLive ? available : null;
        scheduled = useSchedule;
        return applied;
    }

    void afterTick() {
        if ((applied != null || scheduled) && info.getRainTime() == 0 && info.getThunderTime() == 0) {
            if (applied != null) heldObservation = applied;
            if (episode != null && episode.kind.wet) heldEpisode = episode;
            applied = null;
            episode = null;
            scheduled = false;
        }
        last = new Timers(info);
    }

    /** A new explicit admin preview replaces earlier manual leases. */
    void replaceOverride() {
        heldUntil = 0;
        heldObservation = null;
        heldEpisode = null;
        last = new Timers(info);
    }

    WeatherEpisode episode() {
        return episode;
    }

    Observation observation() {
        return applied;
    }

    boolean scheduled() {
        return scheduled;
    }

    private static final class Timers {
        private final int clear;
        private final int rain;
        private final int thunder;
        private final boolean raining;
        private final boolean thundering;

        Timers(WorldInfo info) {
            clear = info.getCleanWeatherTime();
            rain = info.getRainTime();
            thunder = info.getThunderTime();
            raining = info.isRaining();
            thundering = info.isThundering();
        }

        boolean matches(WorldInfo info) {
            return clear == info.getCleanWeatherTime() && rain == info.getRainTime()
                    && thunder == info.getThunderTime() && raining == info.isRaining()
                    && thundering == info.isThundering();
        }

        int changedDuration(WorldInfo info) {
            int changedClear = clear == info.getCleanWeatherTime() ? 0 : info.getCleanWeatherTime();
            int changedRain = rain == info.getRainTime() ? 0 : info.getRainTime();
            int changedThunder = thunder == info.getThunderTime() ? 0 : info.getThunderTime();
            return Math.max(changedClear, Math.max(changedRain, changedThunder));
        }
    }
}
