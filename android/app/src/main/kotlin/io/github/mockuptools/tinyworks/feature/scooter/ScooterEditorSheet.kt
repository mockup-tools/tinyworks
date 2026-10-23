package io.github.mockuptools.tinyworks.feature.scooter

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DraftSaver = mapSaver<SnapshotStateMap<String, String>>(
    save = { it.toMap() }, restore = { saved -> mutableStateMapOf<String, String>().apply { saved.forEach { (k, v) -> put(k, v as String) } } },
)
private val InputDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScooterEditorSheet(
    type: ScooterEditor, id: Long, vehicle: ScooterVehicle?, year: Int, data: ScooterData,
    repository: ScooterRepository, onDismiss: () -> Unit, onSaved: (Long?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ride = data.rides.find { it.id == id }
    val expense = data.expenses.find { it.id == id }
    val reading = data.readings.find { it.vehicleId == vehicle?.id && it.year == year }
    val editingVehicle = vehicle?.takeIf { id != 0L }
    val draft = rememberSaveable(type, id, saver = DraftSaver) {
        mutableStateMapOf<String, String>().apply {
            val now = LocalDateTime.now().withSecond(0).withNano(0)
            when (type) {
                ScooterEditor.VEHICLE -> {
                    val v = editingVehicle
                    putAll(mapOf("model" to (v?.model ?: ""), "plate" to (v?.plate ?: ""),
                        "ownership" to (v?.ownership ?: Ownership.RENTAL).name, "active" to (v?.active ?: true).toString(),
                        "purchaseDate" to (v?.purchaseDate ?: ""), "purchaseYen" to (v?.purchaseYen?.toString() ?: ""),
                        "rentalCompany" to (v?.rentalCompany ?: ""), "rentalStart" to (v?.rentalStart ?: ""), "rentalEnd" to (v?.rentalEnd ?: ""),
                        "rentalMonthlyYen" to (v?.rentalMonthlyYen?.toString() ?: ""), "note" to (v?.note ?: ""), "photo" to (v?.photo ?: "")))
                }
                ScooterEditor.RIDE -> putAll(mapOf(
                    "start" to (ride?.start ?: now).format(InputDateTime), "startOdo" to (ride?.startOdo?.toString() ?: ""),
                    "end" to (ride?.end ?: now).format(InputDateTime), "endOdo" to (ride?.endOdo?.toString() ?: ""),
                    "complete" to (ride != null).toString(), "note" to (ride?.note ?: "")))
                ScooterEditor.ANNUAL -> putAll(mapOf(
                    "startDate" to (reading?.startDate ?: LocalDate.of(year, 1, 1)).toString(), "startOdo" to (reading?.startOdo?.toString() ?: ""),
                    "endDate" to (reading?.endDate ?: minOf(LocalDate.now(), LocalDate.of(year, 12, 31))).toString(), "endOdo" to (reading?.endOdo?.toString() ?: "")))
                else -> putAll(mapOf("date" to (expense?.date ?: LocalDate.now()).toString(), "odo" to (expense?.odo?.toString() ?: ""),
                    "yen" to (expense?.yen?.toString() ?: ""), "liters" to (expense?.liters?.toString() ?: ""), "description" to (expense?.description ?: "")))
            }
        }
    }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    // Imported-but-unsaved photos are removed on cancel, including after rotation via restored draft.
    DisposableEffect(draft) {
        onDispose {
            var current = context
            while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
            if ((current as? android.app.Activity)?.isChangingConfigurations != true && !saved && draft["photo"] != editingVehicle?.photo) {
                deleteScooterPhoto(context, draft["photo"]?.ifBlank { null })
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val name = withContext(Dispatchers.IO) { importScooterPhoto(context, uri) }
                    draft["photo"]?.takeIf { it != editingVehicle?.photo }?.let { deleteScooterPhoto(context, it) }
                    draft["photo"] = name
                } catch (_: Exception) { error = "写真を取り込めませんでした。別の写真を選んでください。" }
                busy = false
            }
        }
    }
    fun value(key: String) = draft[key].orEmpty().trim()
    fun optionalMoney(key: String): Long? = value(key).takeIf { it.isNotBlank() }?.let {
        requireNotNull(it.toLongOrNull()?.takeIf { n -> n in 0..1_000_000_000_000 }) { "金額は0以上の整数で入力してください" }
    }
    fun odo(key: String): Long = requireNotNull(value(key).toLongOrNull()?.takeIf { it in 0..100_000_000 }) { "ODOは0以上の整数で入力してください" }
    fun optionalDate(key: String): String = value(key).also { if (it.isNotEmpty()) LocalDate.parse(it) }
    fun save() {
        busy = true; error = null
        scope.launch {
            try {
                val newVehicleId = withContext(Dispatchers.IO) {
                    when (type) {
                        ScooterEditor.VEHICLE -> {
                            val start = optionalDate("rentalStart"); val end = optionalDate("rentalEnd")
                            require(start.isBlank() || end.isBlank() || LocalDate.parse(start) <= LocalDate.parse(end)) { "レンタル終了日は開始日以降にしてください" }
                            val v = ScooterVehicle(id, value("model"), value("plate"), Ownership.valueOf(value("ownership")),
                                value("active").toBoolean(), optionalDate("purchaseDate"), optionalMoney("purchaseYen"),
                                value("rentalCompany"), start, end, optionalMoney("rentalMonthlyYen"), value("note"), value("photo").ifBlank { null })
                            repository.saveVehicle(v)
                        }
                        ScooterEditor.RIDE -> {
                            val complete = value("complete").toBoolean()
                            repository.saveRide(ScooterRide(id, vehicle!!.id, LocalDateTime.parse(value("start"), InputDateTime), odo("startOdo"),
                                if (complete) LocalDateTime.parse(value("end"), InputDateTime) else null,
                                if (complete) odo("endOdo") else null, value("note")))
                            null
                        }
                        ScooterEditor.ANNUAL -> {
                            repository.saveReading(ScooterAnnualReading(vehicle!!.id, year, LocalDate.parse(value("startDate")), odo("startOdo"), LocalDate.parse(value("endDate")), odo("endOdo")))
                            null
                        }
                        else -> {
                            val fuel = type == ScooterEditor.FUEL
                            val liters = if (fuel) requireNotNull(value("liters").toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 1000 }) { "給油量を正しく入力してください" } else null
                            repository.saveExpense(ScooterExpense(id, vehicle!!.id, if (fuel) ExpenseKind.FUEL else ExpenseKind.MAINTENANCE,
                                LocalDate.parse(value("date")), odo("odo"), requireNotNull(optionalMoney("yen")) { "金額を入力してください" }, liters, value("description")))
                            null
                        }
                    }
                }
                saved = true
                if (type == ScooterEditor.VEHICLE && editingVehicle?.photo != value("photo")) deleteScooterPhoto(context, editingVehicle?.photo)
                onSaved(newVehicleId)
            } catch (e: Exception) {
                error = if (e is java.time.DateTimeException) "日付・時刻の形式を確認してください" else if (e is IllegalArgumentException) e.message ?: "入力内容を確認してください" else "保存できませんでした。もう一度お試しください。"
            } finally { busy = false }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetGesturesEnabled = !busy) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when (type) {
                ScooterEditor.VEHICLE -> if (id == 0L) "車両を登録" else "車両情報を編集"
                ScooterEditor.RIDE -> "業務走行を登録・編集"
                ScooterEditor.ANNUAL -> "${year}年の基準ODO"
                ScooterEditor.FUEL -> "給油を登録・編集"
                ScooterEditor.MAINTENANCE -> "整備を登録・編集"
            }, style = MaterialTheme.typography.titleLarge)
            if (type != ScooterEditor.VEHICLE) Text(vehicle!!.model + " · " + vehicle.plate)
            @Composable fun field(key: String, label: String, numeric: Boolean = false, decimal: Boolean = false, date: Boolean = false, time: Boolean = false, optional: Boolean = false) {
                OutlinedTextField(draft[key].orEmpty(), { draft[key] = it }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text(label + if (optional) "（任意）" else "") }, singleLine = key !in listOf("note", "description"),
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else if (numeric) KeyboardType.Number else KeyboardType.Text),
                    supportingText = if (date || time) ({ Text(if (time) "例：2026-10-23 09:00" else "例：2026-10-23") }) else null,
                )
                if (date || time) TextButton(enabled = !busy, onClick = {
                    val selected = runCatching { if (time) LocalDateTime.parse(value(key), InputDateTime).toLocalDate() else LocalDate.parse(value(key)) }.getOrDefault(LocalDate.now())
                    DatePickerDialog(context, { _, y, m, d ->
                        val chosen = LocalDate.of(y, m + 1, d)
                        if (!time) draft[key] = chosen.toString() else {
                            val clock = runCatching { LocalDateTime.parse(value(key), InputDateTime).toLocalTime() }.getOrDefault(java.time.LocalTime.now())
                            TimePickerDialog(context, { _, h, minute -> draft[key] = chosen.atTime(h, minute).format(InputDateTime) }, clock.hour, clock.minute, true).show()
                        }
                    }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
                }) { Text("日付${if (time) "・時刻" else ""}を選択") }
            }
            @Composable fun toggle(key: String, label: String) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, Modifier.weight(1f))
                    Switch(value(key).toBoolean(), { draft[key] = it.toString() }, enabled = !busy)
                }
            }
            when (type) {
                ScooterEditor.VEHICLE -> {
                    ScooterPhoto(value("photo").ifBlank { null }, value("model"))
                    Row {
                        TextButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("写真を選択・変更") }
                        if (value("photo").isNotBlank()) TextButton(enabled = !busy, onClick = {
                            if (value("photo") != editingVehicle?.photo) deleteScooterPhoto(context, value("photo"))
                            draft["photo"] = ""
                        }) { Text("写真を削除") }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Ownership.entries.forEachIndexed { index, ownership ->
                            SegmentedButton(selected = value("ownership") == ownership.name, onClick = { draft["ownership"] = ownership.name }, enabled = !busy,
                                shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(ownership.label) }
                        }
                    }
                    field("model", "車種"); field("plate", "ナンバー")
                    if (value("ownership") == Ownership.PURCHASE.name) {
                        field("purchaseDate", "購入日", date = true, optional = true)
                        field("purchaseYen", "購入金額（円）", numeric = true, optional = true)
                    } else {
                        field("rentalCompany", "レンタル会社", optional = true)
                        field("rentalStart", "レンタル開始日", date = true, optional = true)
                        field("rentalEnd", "レンタル終了日", date = true, optional = true)
                        field("rentalMonthlyYen", "月額料金（円）", numeric = true, optional = true)
                    }
                    field("note", "メモ", optional = true)
                    toggle("active", "車両を有効にする")
                    Text("区分を切り替えても購入・レンタルそれぞれの入力内容は保持します。無効化した車両も履歴を確認できます。", style = MaterialTheme.typography.bodySmall)
                }
                ScooterEditor.RIDE -> {
                    field("start", "乗車日時", time = true); field("startOdo", "乗車時ODO（km）", numeric = true)
                    toggle("complete", "降車も登録する")
                    if (value("complete").toBoolean()) {
                        field("end", "降車日時", time = true); field("endOdo", "降車時ODO（km）", numeric = true)
                    }
                    field("note", "メモ", optional = true)
                    Text("メーターに表示された累計距離を入力してください。年をまたぐ走行は年末・年始で分けて登録します。", style = MaterialTheme.typography.bodySmall)
                }
                ScooterEditor.ANNUAL -> {
                    Text("年途中に利用を開始した場合は、年始の代わりに利用開始時の値を入力します。最終ODOは集計したい時点の値です。")
                    field("startDate", "年始・利用開始日", date = true); field("startOdo", "年始・利用開始時ODO（km）", numeric = true)
                    field("endDate", "最終・集計日", date = true); field("endOdo", "最終・集計時点ODO（km）", numeric = true)
                }
                else -> {
                    field("date", "日付", date = true); field("odo", "ODO（km）", numeric = true); field("yen", "金額（円）", numeric = true)
                    if (type == ScooterEditor.FUEL) field("liters", "給油量（L）", decimal = true)
                    field("description", if (type == ScooterEditor.FUEL) "メモ" else "整備内容・メモ", optional = type == ScooterEditor.FUEL)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { save() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "処理中…" else "保存") }
            OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("キャンセル") }
            if (id != 0L || (type == ScooterEditor.ANNUAL && reading != null)) TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("削除", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("記録を削除しますか？") }, text = { Text(if (type == ScooterEditor.VEHICLE) "この車両の写真・業務走行・基準ODO・給油・整備の記録をすべて削除します。履歴を残す場合は無効化してください。" else "この記録を削除します。元には戻せません。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        when (type) {
                            ScooterEditor.VEHICLE -> repository.deleteVehicle(id)
                            ScooterEditor.RIDE -> repository.deleteRide(id)
                            ScooterEditor.ANNUAL -> repository.deleteReading(vehicle!!.id, year)
                            else -> repository.deleteExpense(id)
                        }
                    }
                    if (type == ScooterEditor.VEHICLE) deleteScooterPhoto(context, editingVehicle?.photo)
                    confirmDelete = false; onSaved(null)
                } catch (_: Exception) { error = "削除できませんでした"; confirmDelete = false } finally { busy = false }
            }
        }) { Text("削除する") } }, dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("戻る") } })
}
