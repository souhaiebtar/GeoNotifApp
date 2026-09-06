package com.geonotif.app

import org.junit.Assert.*
import org.junit.Test

class ArrivalRulesTest {
    @Test fun coordinatesMustBeFiniteAndInRange() {
        assertTrue(ArrivalRules.validCoordinates(90.0, -180.0))
        assertTrue(ArrivalRules.validCoordinates(0.0, 0.0))
        assertFalse(ArrivalRules.validCoordinates(91.0, 0.0))
        assertFalse(ArrivalRules.validCoordinates(0.0, 181.0))
        assertFalse(ArrivalRules.validCoordinates(Double.NaN, 0.0))
        assertFalse(ArrivalRules.validCoordinates(0.0, Double.POSITIVE_INFINITY))
    }

    @Test fun arrivalIncludesRadiusBoundary() {
        assertTrue(ArrivalRules.isArrival(500f, 10f, 500, 0))
        assertFalse(ArrivalRules.isArrival(500.1f, 10f, 500, 0))
    }

    @Test fun staleAndInaccurateFixesCannotTrigger() {
        assertFalse(ArrivalRules.isArrival(10f, 20f, 500, 30_001))
        assertFalse(ArrivalRules.isArrival(10f, 20f, 500, -1))
        assertFalse(ArrivalRules.isArrival(10f, 501f, 500, 0))
        assertFalse(ArrivalRules.isArrival(10f, Float.NaN, 500, 0))
        assertFalse(ArrivalRules.isArrival(10f, -1f, 500, 0))
        assertTrue(ArrivalRules.isArrival(10f, 100f, 100, 30_000))
    }
}
