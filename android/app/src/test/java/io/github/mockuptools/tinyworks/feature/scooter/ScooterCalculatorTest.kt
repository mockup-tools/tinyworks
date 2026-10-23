package io.github.mockuptools.tinyworks.feature.scooter

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScooterCalculatorTest {
    private val reading = ScooterAnnualReading(1, 2026, LocalDate.of(2026, 5, 1), 12000, LocalDate.of(2026, 10, 23), 17000)
    private fun ride(id: Long = 1, start: String = "2026-05-02T09:00", end: String? = "2026-05-02T18:00", from: Long = 12000, to: Long? = 16000) =
        ScooterRide(id, 1, LocalDateTime.parse(start), from, end?.let(LocalDateTime::parse), to)

    @Test fun calculatesBusinessAndPrivateShareForMidyearRental() {
        val result = ScooterCalculator.summarize(2026, reading, listOf(ride()))
        assertNull(result.issue)
        assertEquals(5000L, result.totalKm)
        assertEquals(4000L, result.businessKm)
        assertEquals(1000L, result.privateKm)
        assertEquals(80.0, result.businessPercent!!, 0.0001)
    }
    @Test fun noBusinessRidesMeansZeroPercent() {
        assertEquals(0.0, ScooterCalculator.summarize(2026, reading, emptyList()).businessPercent!!, 0.0001)
    }
    @Test fun missingReadingAndZeroDistanceNeverProduceRatio() {
        assertNull(ScooterCalculator.summarize(2026, null, listOf(ride())).businessPercent)
        assertNull(ScooterCalculator.summarize(2026, reading.copy(endOdo = 12000), emptyList()).businessPercent)
    }
    @Test fun unfinishedRideBlocksRatio() {
        val result = ScooterCalculator.summarize(2026, reading, listOf(ride(end = null, to = null)))
        assertNull(result.businessPercent)
        assertTrue(result.issue!!.contains("降車"))
    }
    @Test fun ridesOutsideDatedOdoPeriodAreNotSilentlyCounted() {
        val result = ScooterCalculator.summarize(2026, reading.copy(endDate = LocalDate.of(2026, 5, 1)), listOf(ride()))
        assertNull(result.businessPercent)
    }
    @Test fun outOfRangeOrReversedOdoIsRejected() {
        assertNull(ScooterCalculator.summarize(2026, reading.copy(endOdo = 15000), listOf(ride())).businessPercent)
        assertNotNull(ScooterCalculator.rideIssue(ride(from = 50, to = 40), emptyList()))
        assertNull(ScooterCalculator.summarize(2026, reading.copy(startOdo = 18000), emptyList()).businessPercent)
    }
    @Test fun chronologicalOdoReversalIsRejectedEvenWhenTimesDoNotOverlap() {
        val previous = ride(to = 16000)
        val next = ride(2, "2026-05-03T09:00", "2026-05-03T10:00", 15000, 15500)
        assertNotNull(ScooterCalculator.rideIssue(next, listOf(previous)))
    }
    @Test fun overlappingRidesAndSecondOpenRideAreRejected() {
        assertNotNull(ScooterCalculator.rideIssue(ride(2), listOf(ride())))
        assertNotNull(ScooterCalculator.rideIssue(ride(2, end = null, to = null), listOf(ride(end = null, to = null))))
    }
    @Test fun editingSameRecordDoesNotOverlapItself() {
        assertNull(ScooterCalculator.rideIssue(ride(), listOf(ride())))
    }
    @Test fun differentVehiclesHaveIndependentOdoAndRides() {
        assertNull(ScooterCalculator.rideIssue(ride(), listOf(ride(2).copy(vehicleId = 2))))
        assertEquals(0.0, ScooterCalculator.summarize(2026, reading,
            listOf(ride().copy(vehicleId = 2))).businessPercent!!, 0.0001)
    }
    @Test fun crossingYearBoundaryRequiresSplit() {
        assertNotNull(ScooterCalculator.rideIssue(ride(start = "2026-12-31T23:00", end = "2027-01-01T01:00"), emptyList()))
    }
    @Test fun previousYearDoesNotAffectCurrentYear() {
        assertEquals(0.0, ScooterCalculator.summarize(2026, reading,
            listOf(ride(start = "2025-05-02T09:00", end = "2025-05-02T18:00"))).businessPercent!!, 0.0001)
    }
    @Test fun partialEndAndEndBeforeStartAreRejected() {
        assertNotNull(ScooterCalculator.rideIssue(ride(to = null), emptyList()))
        assertNotNull(ScooterCalculator.rideIssue(ride(end = "2026-05-02T08:00"), emptyList()))
    }
    @Test fun fullBusinessUseIsOneHundredPercent() {
        assertEquals(100.0, ScooterCalculator.summarize(2026, reading,
            listOf(ride(to = 17000))).businessPercent!!, 0.0001)
    }
}
