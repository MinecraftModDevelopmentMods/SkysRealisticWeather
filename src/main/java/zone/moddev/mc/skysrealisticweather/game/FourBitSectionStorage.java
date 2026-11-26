package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.util.BitArray;

/** A four-bit palette index always fits entirely within one word of the section's native backing array. */
public final class FourBitSectionStorage extends BitArray {
    private static final int SECTION_SIZE = 4096;

    private FourBitSectionStorage() {
        super(4, SECTION_SIZE);
    }

    public static BitArray create(int bits, int size) {
        return bits == 4 && size == SECTION_SIZE ? new FourBitSectionStorage() : new BitArray(bits, size);
    }

    @Override
    public int getAt(int index) {
        if (index < 0 || index >= SECTION_SIZE) return super.getAt(index);
        int shift = (index & 15) << 2;
        return (int) (getBackingLongArray()[index >>> 4] >>> shift) & 15;
    }

    @Override
    public void setAt(int index, int value) {
        if (index < 0 || index >= SECTION_SIZE || (value & ~15) != 0) {
            super.setAt(index, value);
            return;
        }
        int word = index >>> 4;
        int shift = (index & 15) << 2;
        long[] data = getBackingLongArray();
        data[word] = (data[word] & ~(15L << shift)) | ((long) value << shift);
    }
}
