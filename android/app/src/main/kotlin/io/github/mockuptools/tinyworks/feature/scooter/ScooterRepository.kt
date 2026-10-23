package io.github.mockuptools.tinyworks.feature.scooter

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.mockuptools.tinyworks.core.storage.TinyworksDatabase
import io.github.mockuptools.tinyworks.core.storage.TinyworksDatabaseModule
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

private object ScooterDatabaseModule : TinyworksDatabaseModule {
    override val moduleName = "scooter"
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS scooter_vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT NOT NULL)")
        for (table in listOf("scooter_rides", "scooter_expenses")) {
            database.execSQL("CREATE TABLE IF NOT EXISTS $table (id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL, data TEXT NOT NULL)")
            database.execSQL("CREATE INDEX IF NOT EXISTS ${table}_vehicle ON $table(vehicle_id)")
        }
        database.execSQL("CREATE TABLE IF NOT EXISTS scooter_annual (vehicle_id INTEGER NOT NULL, year INTEGER NOT NULL, data TEXT NOT NULL, PRIMARY KEY(vehicle_id, year))")
    }
}

data class ScooterData(
    val vehicles: List<ScooterVehicle> = emptyList(),
    val rides: List<ScooterRide> = emptyList(),
    val readings: List<ScooterAnnualReading> = emptyList(),
    val expenses: List<ScooterExpense> = emptyList(),
)

/** Feature-owned version-1 JSON payloads; row identity and vehicle ownership remain SQL columns. */
class ScooterRepository(context: Context) {
    private val helper = TinyworksDatabase.getInstance(context, listOf(ScooterDatabaseModule))

    fun load(): ScooterData = ScooterData(
        rows("scooter_vehicles").map { (id, j) ->
            ScooterVehicle(id, j.getString("model"), j.getString("plate"), Ownership.valueOf(j.getString("ownership")),
                j.getBoolean("active"), j.optString("purchaseDate"), j.longOrNull("purchaseYen"),
                j.optString("rentalCompany"), j.optString("rentalStart"), j.optString("rentalEnd"),
                j.longOrNull("rentalMonthlyYen"), j.optString("note"), j.stringOrNull("photo"))
        }.sortedWith(compareByDescending<ScooterVehicle> { it.active }.thenBy { it.id }),
        rows("scooter_rides").map { (id, j) ->
            ScooterRide(id, j.getLong("vehicleId"), LocalDateTime.parse(j.getString("start")), j.getLong("startOdo"),
                j.stringOrNull("end")?.let(LocalDateTime::parse), j.longOrNull("endOdo"), j.optString("note"))
        }.sortedByDescending { it.start },
        rows("scooter_annual", "year").map { (_, j) ->
            ScooterAnnualReading(j.getLong("vehicleId"), j.getInt("year"), LocalDate.parse(j.getString("startDate")),
                j.getLong("startOdo"), LocalDate.parse(j.getString("endDate")), j.getLong("endOdo"))
        },
        rows("scooter_expenses").map { (id, j) ->
            ScooterExpense(id, j.getLong("vehicleId"), ExpenseKind.valueOf(j.getString("kind")),
                LocalDate.parse(j.getString("date")), j.getLong("odo"), j.getLong("yen"),
                if (j.isNull("liters")) null else j.getDouble("liters"), j.optString("description"))
        }.sortedByDescending { it.date },
    )

    fun saveVehicle(v: ScooterVehicle): Long {
        require(v.model.isNotBlank() && v.plate.isNotBlank()) { "車種とナンバーを入力してください" }
        require(v.purchaseYen == null || v.purchaseYen >= 0)
        require(v.rentalMonthlyYen == null || v.rentalMonthlyYen >= 0)
        return write("scooter_vehicles", v.id, null, json(
            "model" to v.model, "plate" to v.plate, "ownership" to v.ownership.name, "active" to v.active,
            "purchaseDate" to v.purchaseDate, "purchaseYen" to v.purchaseYen, "rentalCompany" to v.rentalCompany,
            "rentalStart" to v.rentalStart, "rentalEnd" to v.rentalEnd, "rentalMonthlyYen" to v.rentalMonthlyYen,
            "note" to v.note, "photo" to v.photo,
        ))
    }

    fun saveRide(r: ScooterRide) = transaction {
        requireVehicle(r.vehicleId)
        require(r.start <= LocalDateTime.now() && (r.end == null || r.end <= LocalDateTime.now())) { "未来の走行は登録できません" }
        ScooterCalculator.rideIssue(r, load().rides)?.let { throw IllegalArgumentException(it) }
        write("scooter_rides", r.id, r.vehicleId, json("vehicleId" to r.vehicleId, "start" to r.start,
            "startOdo" to r.startOdo, "end" to r.end, "endOdo" to r.endOdo, "note" to r.note))
    }

    fun saveReading(r: ScooterAnnualReading) {
        requireVehicle(r.vehicleId)
        require(r.year in 1900..LocalDate.now().year && r.startDate.year == r.year && r.endDate.year == r.year &&
            r.startDate <= r.endDate && r.endDate <= LocalDate.now() && r.startOdo >= 0 && r.endOdo >= r.startOdo
        ) { "年度・期間・ODOの大小を確認してください" }
        val values = ContentValues().apply {
            put("vehicle_id", r.vehicleId); put("year", r.year)
            put("data", json("vehicleId" to r.vehicleId, "year" to r.year, "startDate" to r.startDate,
                "startOdo" to r.startOdo, "endDate" to r.endDate, "endOdo" to r.endOdo).toString())
        }
        helper.writableDatabase.insertWithOnConflict("scooter_annual", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            .also { check(it != -1L) { "保存できませんでした" } }
    }

    fun saveExpense(e: ScooterExpense): Long {
        requireVehicle(e.vehicleId)
        require(e.odo >= 0 && e.yen >= 0 && e.date <= LocalDate.now()) { "日付・ODO・金額を確認してください" }
        require(e.kind != ExpenseKind.FUEL || (e.liters != null && e.liters.isFinite() && e.liters > 0)) { "給油量を入力してください" }
        require(e.kind != ExpenseKind.MAINTENANCE || e.description.isNotBlank()) { "整備内容を入力してください" }
        return write("scooter_expenses", e.id, e.vehicleId, json("vehicleId" to e.vehicleId, "kind" to e.kind.name,
            "date" to e.date, "odo" to e.odo, "yen" to e.yen, "liters" to e.liters, "description" to e.description))
    }

    fun deleteRide(id: Long) { helper.writableDatabase.delete("scooter_rides", "id = ?", arrayOf(id.toString())) }
    fun deleteExpense(id: Long) { helper.writableDatabase.delete("scooter_expenses", "id = ?", arrayOf(id.toString())) }
    fun deleteReading(vehicleId: Long, year: Int) {
        helper.writableDatabase.delete("scooter_annual", "vehicle_id = ? AND year = ?", arrayOf(vehicleId.toString(), year.toString()))
    }

    fun deleteVehicle(id: Long) = transaction {
        for (table in listOf("scooter_rides", "scooter_expenses", "scooter_annual")) {
            helper.writableDatabase.delete(table, "vehicle_id = ?", arrayOf(id.toString()))
        }
        helper.writableDatabase.delete("scooter_vehicles", "id = ?", arrayOf(id.toString()))
    }

    private fun requireVehicle(id: Long) {
        helper.readableDatabase.rawQuery("SELECT id FROM scooter_vehicles WHERE id = ?", arrayOf(id.toString())).use {
            require(it.moveToFirst()) { "車両が見つかりません" }
        }
    }

    private fun rows(table: String, identity: String = "id"): List<Pair<Long, JSONObject>> =
        helper.readableDatabase.query(table, arrayOf(identity, "data"), null, null, null, null, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to JSONObject(cursor.getString(1))) }
        }

    private fun write(table: String, id: Long, vehicleId: Long?, data: JSONObject): Long {
        val values = ContentValues().apply {
            put("data", data.toString())
            vehicleId?.let { put("vehicle_id", it) }
        }
        return if (id == 0L) helper.writableDatabase.insertOrThrow(table, null, values) else {
            check(helper.writableDatabase.update(table, values, "id = ?", arrayOf(id.toString())) == 1) { "記録が見つかりません" }
            id
        }
    }

    private fun <T> transaction(block: () -> T): T {
        val db = helper.writableDatabase
        db.beginTransaction()
        try { val result = block(); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
}

private fun json(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
    put("version", 1)
    pairs.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
