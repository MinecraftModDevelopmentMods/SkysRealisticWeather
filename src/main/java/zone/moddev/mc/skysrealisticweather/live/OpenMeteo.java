package zone.moddev.mc.skysrealisticweather.live;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import zone.moddev.mc.skysrealisticweather.live.Observation.Condition;

public final class OpenMeteo implements WeatherProvider {
    private static final int MAX_RESPONSE_BYTES = 16384;

    @Override
    public Observation fetch(LiveLocation location) throws IOException {
        URL endpoint = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + location.latitude
                + "&longitude=" + location.longitude + "&current=temperature_2m,weather_code"
                + "&temperature_unit=celsius&timeformat=unixtime");
        HttpsURLConnection connection = (HttpsURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "SkysRealisticWeather/0.1");
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Weather service returned HTTP " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > MAX_RESPONSE_BYTES) throw new IOException("Weather response too large");
                    body.write(buffer, 0, count);
                }
                return parse(new String(body.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    static Observation parse(String body) throws IOException {
        try {
            JsonObject current = new JsonParser().parse(body).getAsJsonObject().getAsJsonObject("current");
            long time = integer(current, "time");
            int code = Math.toIntExact(integer(current, "weather_code"));
            JsonElement temp = current.get("temperature_2m");
            if (!temp.isJsonPrimitive() || !temp.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Expected a temperature number");
            }
            return new Observation(time, temp.getAsFloat(), classify(code));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid weather response", invalid);
        }
    }

    private static long integer(JsonObject current, String key) {
        JsonElement element = current.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected a number for " + key);
        }
        return element.getAsBigDecimal().longValueExact();
    }

    static Condition classify(int code) {
        switch (code) {
            case 0: case 1: case 2: case 3: return Condition.CLEAR;
            case 45: case 48: return Condition.FOG;
            case 51: case 53: case 55: case 56: case 57:
            case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82: return Condition.RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return Condition.SNOW;
            case 95: case 96: case 97: case 99: return Condition.THUNDER;
            default: throw new IllegalArgumentException("Unknown weather code");
        }
    }
}
