package com.sdprep.urlshortener.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62Test {

    @Test
    void encodesSingleDigits() {
        assertEquals("0", Base62.encode(0));
        assertEquals("9", Base62.encode(9));
        assertEquals("a", Base62.encode(10));
        assertEquals("Z", Base62.encode(61));
    }

    @Test
    void carriesIntoNextDigitAt62() {
        assertEquals("10", Base62.encode(62));
        assertEquals("1000", Base62.encode(62L * 62 * 62));
    }

    @Test
    void sevenCharactersCoverTrillionsOfIds() {
        assertEquals("ZZZZZZZ", Base62.encode(3_521_614_606_207L)); // 62^7 - 1
    }

    @Test
    void rejectsNegativeIds() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
    }
}
