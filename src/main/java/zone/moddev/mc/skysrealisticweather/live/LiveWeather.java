package zone.moddev.mc.skysrealisticweather.live;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LiveWeather implements AutoCloseable {
    private static final Logger LOG = LogManager.getLogger("Sky's Realistic Weather");
    private final ScheduledExecutorService worker;
    private final Clock clock;
    private volatile Observation latest;
    private boolean failed;

    public LiveWeather(LiveLocation location) {
        this(location, new OpenMeteo(), Clock.systemUTC(), 15, TimeUnit.MINUTES);
    }

    LiveWeather(LiveLocation location, WeatherProvider provider, Clock clock, long interval, TimeUnit unit) {
        this.clock = clock;
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Sky's Realistic Weather provider");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(() -> poll(location, provider), 0, interval, unit);
    }

    private void poll(LiveLocation location, WeatherProvider provider) {
        try {
            Observation result = provider.fetch(location);
            if (!result.fresh(clock.instant().getEpochSecond())
                    || latest != null && result.observedAt < latest.observedAt) {
                throw new IllegalStateException("Weather observation is out of date");
            }
            latest = result;
            if (failed) LOG.info("Live weather feed recovered.");
            failed = false;
        } catch (Exception unavailable) {
            if (!failed && !Thread.currentThread().isInterrupted()) {
                LOG.warn("Live weather feed unavailable. Seasonal weather applies when the latest reading expires.");
            }
            failed = true;
        }
    }

    public Observation current() {
        Observation result = latest;
        return result != null && result.fresh(clock.instant().getEpochSecond()) ? result : null;
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
