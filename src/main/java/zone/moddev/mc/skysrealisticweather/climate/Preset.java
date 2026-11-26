package zone.moddev.mc.skysrealisticweather.climate;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import zone.moddev.mc.skysrealisticweather.api.Season;

public final class Preset {
    public static final String DEFAULT_ID = "burlington";
    public static final String CUSTOM_ID = "custom";
    private static final String[] SEASON_LABELS = {"Spring", "Summer", "Autumn", "Winter"};
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public enum Group {
        TROPICAL("Tropical"), DRY("Dry"), TEMPERATE("Temperate"), CONTINENTAL("Continental"),
        POLAR("Polar"), HIGHLANDS("Highlands"), FANTASY("Fantasy"), CALENDAR("Calendar"),
        USER("User Presets");

        public final String label;

        Group(String label) {
            this.label = label;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Group parse(String key) {
            return valueOf(key.toUpperCase(Locale.ROOT));
        }
    }

    public final String id;
    public final String label;
    public final String description;
    public final Group group;
    public final ClimateProfile profile;
    public final WeatherPattern weather;
    private final String[] phaseLabels;

    public Preset(String id, String label, String description, Group group, ClimateProfile profile,
                  String[] phaseLabels) {
        this(id, label, description, group, profile, phaseLabels, WeatherPattern.defaults(id));
    }

    public Preset(String id, String label, String description, Group group, ClimateProfile profile,
                  String[] phaseLabels, WeatherPattern weather) {
        if (!ID.matcher(id).matches() || CUSTOM_ID.equals(id)) throw new IllegalArgumentException("Invalid preset ID");
        if (phaseLabels.length != ClimateProfile.SEASON_COUNT) {
            throw new IllegalArgumentException("Expected four phase labels");
        }
        this.id = id;
        this.label = text(label, "Name", false, 64);
        this.description = text(description, "Description", true, 240);
        this.group = Objects.requireNonNull(group);
        this.profile = Objects.requireNonNull(profile);
        this.weather = Objects.requireNonNull(weather);
        this.phaseLabels = phaseLabels.clone();
        for (int index = 0; index < this.phaseLabels.length; index++) {
            this.phaseLabels[index] = text(this.phaseLabels[index], "Phase label", false, 32);
        }
    }

    public String phaseLabel(Season season) {
        return phaseLabels[season.ordinal()];
    }

    public String[] phaseLabels() {
        return phaseLabels.clone();
    }

    public static String defaultPhaseLabel(Season season) {
        return SEASON_LABELS[season.ordinal()];
    }

    static String normalizeId(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    static String text(String value, String field, boolean allowEmpty, int maxLength) {
        String trimmed = value.trim();
        if ((!allowEmpty && trimmed.isEmpty()) || trimmed.length() > maxLength
                || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must contain "
                    + (allowEmpty ? "at most " : "1 to ") + maxLength + " characters on one line");
        }
        return trimmed;
    }
}
