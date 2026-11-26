package zone.moddev.mc.skysrealisticweather.game;

import net.minecraft.util.BitArray;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FourBitSectionStorageTest {
    @Test
    void everyNibbleMatchesNativeReadsWritesAndBackingWords() {
        BitArray nativeBits = new BitArray(4, 4096);
        BitArray bits = FourBitSectionStorage.create(4, 4096);
        assertEquals(FourBitSectionStorage.class, bits.getClass());
        for (int pass = 0; pass < 16; pass++) {
            for (int index = 0; index < 4096; index++) {
                int value = (pass + index) & 15;
                nativeBits.setAt(index, value);
                bits.setAt(index, value);
            }
            assertArrayEquals(nativeBits.getBackingLongArray(), bits.getBackingLongArray());
            for (int index = 0; index < 4096; index++) assertEquals(nativeBits.getAt(index), bits.getAt(index));
        }
        bits.getBackingLongArray()[0] = -1L;
        assertEquals(15, bits.getAt(0));
        assertThrows(IllegalArgumentException.class, () -> bits.getAt(-1));
        assertThrows(IllegalArgumentException.class, () -> bits.setAt(4096, 0));
        assertThrows(IllegalArgumentException.class, () -> bits.setAt(0, 16));
        assertEquals(BitArray.class, FourBitSectionStorage.create(5, 4096).getClass());
        assertEquals(BitArray.class, FourBitSectionStorage.create(4, 16).getClass());
    }
}
