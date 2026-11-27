package zone.moddev.mc.skysrealisticweather.client;

import java.util.Arrays;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Property;
import zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.Unit;

/** Converts GUI values only; the underlying Forge config keeps its original MC units. */
public final class TemperatureConfigElement extends ConfigElement {
    private final Unit unit;
    private final boolean profiles;
    private final boolean celsiusAdjustment;
    public TemperatureConfigElement(Property property, Unit unit, boolean profiles) { this(property,unit,profiles,false); }
    public TemperatureConfigElement(Property property, Unit unit, boolean profiles, boolean celsiusAdjustment) { super(property); this.unit = unit; this.profiles = profiles; this.celsiusAdjustment=celsiusAdjustment; }
    private String heatSource() { String name=super.getName().replace("BonusCelsius","");return Character.toUpperCase(name.charAt(0))+name.substring(1); }
    @Override public String getName() { return celsiusAdjustment?heatSource()+" warmth bonus (Δ"+unit.symbol+")":profiles ? "Profiles (" + unit.symbol + ")" : "Temperature adjustment (" + unit.symbol + ")"; }
    @Override public String getLanguageKey() { return getName(); }
    @Override public String getComment() { return celsiusAdjustment?"Temperature increase at the "+heatSource().toLowerCase(java.util.Locale.ROOT)+" source in "+unit.symbol+". Tapers to zero at its radius; heaters use the strongest bonus. The config file stores Celsius; restart the server to apply.":profiles ? "Rows: id,minimum,ideal minimum,ideal maximum,maximum in " + unit.symbol + ". The config file stores MC units."
            : "Seasonal temperature change in " + unit.symbol + ", not an absolute temperature. The config file stores MC units."; }
    private double degrees(Object value) { double raw=Double.parseDouble(value.toString());return celsiusAdjustment?(unit==Unit.FAHRENHEIT?raw*1.8:raw):unit.adjustment(raw); }
    private String display(Object value) { return Float.toString((float)degrees(value)); }
    @Override public Object get() { return profiles ? super.get() : display(super.get()); }
    @Override public Object getDefault() { return profiles ? super.getDefault() : display(super.getDefault()); }
    @Override public Object getMinValue() { return profiles ? super.getMinValue() : degrees(super.getMinValue()); }
    @Override public Object getMaxValue() { return profiles ? super.getMaxValue() : degrees(super.getMaxValue()); }
    @Override public void set(Object value) {
        if (!value.toString().equals(get().toString())) {
            double number=Double.parseDouble(value.toString());
            super.set(celsiusAdjustment?Double.toString(unit==Unit.FAHRENHEIT?number/1.8:number):Float.toString(unit.fromAdjustment(number)));
        }
    }
    private Object[] rows(Object[] source, boolean toDisplay) {
        Object[] result = source.clone();
        for (int n = 0; n < source.length; n++) {
            String[] row = source[n].toString().split(",", -1);
            if (row.length != 5) continue;
            try {
                for (int i = 1; i < 5; i++) {
                    float value = Float.parseFloat(row[i]);
                    if (!Float.isFinite(value)) throw new NumberFormatException();
                    row[i] = Float.toString(toDisplay ? (float) unit.temperature(value) : unit.fromTemperature(value));
                }
                result[n] = String.join(",", row);
            } catch (NumberFormatException invalid) { /* Keep malformed rows available for correction. */ }
        }
        return result;
    }
    @Override public Object[] getList() { return rows(super.getList(), true); }
    @Override public Object[] getDefaults() { return rows(super.getDefaults(), true); }
    @Override public void set(Object[] values) { if (!Arrays.equals(values, getList())) super.set(rows(values, false)); }
}
