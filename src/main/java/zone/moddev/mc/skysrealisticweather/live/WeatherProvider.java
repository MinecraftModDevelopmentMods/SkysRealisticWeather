package zone.moddev.mc.skysrealisticweather.live;

import java.io.IOException;

interface WeatherProvider {
    Observation fetch(LiveLocation location) throws IOException;
}
