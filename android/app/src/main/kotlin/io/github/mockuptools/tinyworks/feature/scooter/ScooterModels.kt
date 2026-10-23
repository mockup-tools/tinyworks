package io.github.mockuptools.tinyworks.feature.scooter

import java.time.LocalDate
import java.time.LocalDateTime

enum class Ownership(val label: String) { PURCHASE("購入"), RENTAL("レンタル") }
enum class ExpenseKind(val label: String) { FUEL("給油"), MAINTENANCE("整備") }

data class ScooterVehicle(
    val id: Long = 0,
    val model: String,
    val plate: String,
    val ownership: Ownership = Ownership.RENTAL,
    val active: Boolean = true,
    val purchaseDate: String = "",
    val purchaseYen: Long? = null,
    val rentalCompany: String = "",
    val rentalStart: String = "",
    val rentalEnd: String = "",
    val rentalMonthlyYen: Long? = null,
    val note: String = "",
    val photo: String? = null,
)

data class ScooterRide(
    val id: Long = 0,
    val vehicleId: Long,
    val start: LocalDateTime,
    val startOdo: Long,
    val end: LocalDateTime? = null,
    val endOdo: Long? = null,
    val note: String = "",
) {
    val distance: Long? get() = endOdo?.minus(startOdo)
}

data class ScooterAnnualReading(
    val vehicleId: Long,
    val year: Int,
    val startDate: LocalDate,
    val startOdo: Long,
    val endDate: LocalDate,
    val endOdo: Long,
)

data class ScooterExpense(
    val id: Long = 0,
    val vehicleId: Long,
    val kind: ExpenseKind,
    val date: LocalDate,
    val odo: Long,
    val yen: Long,
    val liters: Double? = null,
    val description: String = "",
)

data class ScooterSummary(
    val totalKm: Long? = null,
    val businessKm: Long = 0,
    val businessPercent: Double? = null,
    val issue: String? = null,
) {
    val privateKm: Long? get() = totalKm?.minus(businessKm)
}

object ScooterCalculator {
    /** Completed rides must fit the dated ODO interval; never silently include partial rides. */
    fun summarize(year: Int, reading: ScooterAnnualReading?, rides: List<ScooterRide>): ScooterSummary {
        val annualRides = rides.filter {
            (reading == null || it.vehicleId == reading.vehicleId) && (it.start.year == year || it.end?.year == year)
        }
        val business = annualRides.sumOf { it.distance ?: 0 }
        fun invalid(message: String) = ScooterSummary(businessKm = business, issue = message)
        if (reading == null) return invalid("年始・利用開始時と最終のODOを登録してください")
        if (reading.year != year || reading.startDate.year != year || reading.endDate.year != year ||
            reading.endDate < reading.startDate || reading.startOdo < 0 || reading.endOdo < reading.startOdo
        ) return invalid("年度・期間・基準ODOを確認してください")
        if (annualRides.any { rideIssue(it, annualRides) != null }) return invalid("業務走行の日時・ODOを確認してください")
        if (annualRides.any { it.end == null }) return invalid("降車が未登録の業務走行があります")
        if (annualRides.any {
                it.start.toLocalDate() < reading.startDate || it.end!!.toLocalDate() > reading.endDate ||
                    it.startOdo < reading.startOdo || it.endOdo!! > reading.endOdo
            }
        ) return invalid("業務走行を含む期間・基準ODOを設定してください")
        val total = reading.endOdo - reading.startOdo
        if (total == 0L) return invalid("総走行距離が0 kmのため割合は計算できません")
        if (business > total) return invalid("業務走行距離が総走行距離を超えています")
        return ScooterSummary(total, business, business.toDouble() / total * 100)
    }

    fun rideIssue(ride: ScooterRide, others: List<ScooterRide>): String? {
        if (ride.startOdo < 0 || (ride.endOdo != null && ride.endOdo < ride.startOdo)) return "降車ODOは乗車ODO以上にしてください"
        if ((ride.end == null) != (ride.endOdo == null)) return "降車日時とODOは両方入力してください"
        if (ride.end != null && ride.end < ride.start) return "降車日時は乗車日時以降にしてください"
        if (ride.end != null && ride.end.year != ride.start.year) return "年をまたぐ走行は年末・年始で分けて登録してください"
        val peers = others.filter { it.id != ride.id && it.vehicleId == ride.vehicleId }
        if (peers.any { it.end == null } && ride.end == null) return "この車両には乗車中の記録があります"
        if (peers.any {
                (ride.end == null || it.start < ride.end) && (it.end == null || ride.start < it.end)
            }
        ) return "別の業務走行と時間が重なっています"
        if (peers.any {
                (it.end != null && it.end <= ride.start && it.endOdo!! > ride.startOdo) ||
                    (ride.end != null && ride.end <= it.start && ride.endOdo!! > it.startOdo)
            }
        ) return "前後の業務走行とODOが逆転しています"
        return null
    }
}
