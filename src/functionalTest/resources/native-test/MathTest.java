package com.example.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MathTest {
    @Test
    public void testAdd() {
        System.out.println("Running native test...");
        
        // The library should be loaded automatically by the static initializer of the generated class
        // Call the native function
        final int result = math_lib_h.add(10, 20);
        assertEquals(30, result, "Native add(10, 20) should return 30");
        System.out.println("Native add(10, 20) returned: " + result);
    }
}
