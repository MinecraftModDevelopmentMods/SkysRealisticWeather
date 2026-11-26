package zone.moddev.mc.skysrealisticweather.live;

public final class LiveLocation {
    public static final LiveLocation BURLINGTON = new LiveLocation(44.4683, -73.1500);
    public static final LiveLocation OXFORD = new LiveLocation(51.761, -1.262);

    public final double latitude;
    public final double longitude;

    public LiveLocation(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid weather coordinates");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
