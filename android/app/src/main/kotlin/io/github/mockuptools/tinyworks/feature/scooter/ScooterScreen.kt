package io.github.mockuptools.tinyworks.feature.scooter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

internal enum class ScooterEditor { VEHICLE, RIDE, ANNUAL, FUEL, MAINTENANCE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScooterScreen(modifier: Modifier = Modifier, onNavigateHome: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repository by remember { mutableStateOf<ScooterRepository?>(null) }
    var data by remember { mutableStateOf(ScooterData()) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var editor by rememberSaveable { mutableStateOf<ScooterEditor?>(null) }
    var editId by rememberSaveable { mutableLongStateOf(0) }
    var showActions by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var updatingOwnership by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun load() {
        try {
            data = withContext(Dispatchers.IO) {
                val repo = repository ?: ScooterRepository(context).also { repository = it }
                repo.load()
            }
            error = null
        } catch (_: Exception) { error = "データを読み込めませんでした。再読み込みしてください。" }
        loading = false
    }
    LaunchedEffect(Unit) { load() }
    fun open(type: ScooterEditor, id: Long = 0) { editId = id; editor = type; showActions = false }
    val vehicle = data.vehicles.find { it.id == selectedId }
    BackHandler {
        when {
            editor != null -> editor = null
            showActions -> showActions = false
            selectedId != null -> selectedId = null
            else -> onNavigateHome()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!loading && error == null) FloatingActionButton(onClick = {
                if (vehicle == null) open(ScooterEditor.VEHICLE) else showActions = true
            }) { Text("＋", fontSize = 28.sp, modifier = Modifier.padding(4.dp)) }
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(Modifier.padding(padding).padding(24.dp)) {
                Text(error!!)
                Button(onClick = { loading = true; scope.launch { load() } }) { Text("再読み込み") }
            }
            vehicle == null -> LazyColumn(
                Modifier.padding(padding), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("スクーター管理", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                item { Text("使用中 ${data.vehicles.count { it.active }}台", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (data.vehicles.isEmpty()) item {
                    InfoCard { Text("車両を登録しましょう", style = MaterialTheme.typography.titleMedium); Text("右下の＋からレンタル・購入車両を登録できます。データは端末内に保存されます。") }
                }
                items(data.vehicles, key = { it.id }) { v ->
                    OutlinedCard(onClick = { selectedId = v.id }, modifier = Modifier.fillMaxWidth().alpha(if (v.active) 1f else .48f)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ScooterPhoto(v.photo, v.model)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(v.model, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(v.plate)
                                Surface(color = if (v.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
                                    Text(if (v.active) v.ownership.label else "無効 · ${v.ownership.label}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
                                }
                            }
                            Text("›", fontSize = 26.sp)
                        }
                    }
                }
            }
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedId = null }) { Text("‹ 一覧") }
                    Column(Modifier.weight(1f)) {
                        Text(vehicle.model, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(vehicle.plate + if (!vehicle.active) " · 無効" else "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val pager = rememberPagerState { 4 }
                val tabs = listOf("ダッシュ\nボード", "車両\n情報", "給油\n情報", "整備\n情報")
                TabRow(selectedTabIndex = pager.currentPage) {
                    tabs.forEachIndexed { index, label ->
                        Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(label, maxLines = 2, fontSize = 12.sp) })
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                    val rides = data.rides.filter { it.vehicleId == vehicle.id }
                    val expenses = data.expenses.filter { it.vehicleId == vehicle.id }
                    val reading = data.readings.find { it.vehicleId == vehicle.id && it.year == year }
                    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { if (year > 1900) year-- }) { Text("‹ 前年") }
                                Text("${year}年", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                TextButton(onClick = { year++ }, enabled = year < LocalDate.now().year) { Text("翌年 ›") }
                            }
                        }
                        when (page) {
                            0 -> {
                                item { ScooterDashboard(year, reading, rides, expenses, onEditAnnual = { open(ScooterEditor.ANNUAL) }) }
                                val ongoing = rides.find { it.end == null }
                                if (ongoing != null) item {
                                    InfoCard {
                                        Text("● 業務走行中", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("${dateTime(ongoing.start.toString())} · ${number(ongoing.startOdo)} kmから")
                                        Button(onClick = { open(ScooterEditor.RIDE, ongoing.id) }) { Text("降車ODOを登録") }
                                    }
                                }
                                item { Text("業務走行の履歴", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                val annualRides = rides.filter { it.start.year == year }
                                if (annualRides.isEmpty()) item { Text("この年の業務走行はまだありません。＋から乗車を登録できます。") }
                                items(annualRides, key = { "ride-${it.id}" }) { ride ->
                                    OutlinedCard(onClick = { open(ScooterEditor.RIDE, ride.id) }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(dateTime(ride.start.toString()), fontWeight = FontWeight.Bold)
                                            Text("${number(ride.startOdo)} → ${ride.endOdo?.let(::number) ?: "乗車中"} km")
                                            Text(ride.end?.let { "${dateTime(it.toString())}まで · ${number(ride.distance!!)} km" } ?: "タップして降車を登録")
                                            if (ride.note.isNotBlank()) Text(ride.note)
                                        }
                                    }
                                }
                            }
                            1 -> {
                                item {
                                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                        Ownership.entries.forEachIndexed { index, ownership ->
                                            SegmentedButton(
                                                selected = vehicle.ownership == ownership,
                                                enabled = !updatingOwnership,
                                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                                onClick = {
                                                    updatingOwnership = true
                                                    scope.launch {
                                                        try {
                                                            withContext(Dispatchers.IO) { repository!!.saveVehicle(vehicle.copy(ownership = ownership)) }
                                                            load()
                                                        } catch (_: Exception) { snackbar.showSnackbar("区分を保存できませんでした") }
                                                        finally { updatingOwnership = false }
                                                    }
                                                },
                                            ) { Text(ownership.label) }
                                        }
                                    }
                                }
                                item {
                                    InfoCard {
                                        ScooterPhoto(vehicle.photo, vehicle.model)
                                        DetailRow("車種", vehicle.model); DetailRow("ナンバー", vehicle.plate)
                                        DetailRow("区分", vehicle.ownership.label)
                                        if (vehicle.ownership == Ownership.PURCHASE) {
                                            DetailRow("購入日", vehicle.purchaseDate.ifBlank { "未登録" })
                                            DetailRow("購入金額", vehicle.purchaseYen?.let(::yen) ?: "未登録")
                                        } else {
                                            DetailRow("レンタル会社", vehicle.rentalCompany.ifBlank { "未登録" })
                                            DetailRow("開始日", vehicle.rentalStart.ifBlank { "未登録" })
                                            DetailRow("終了日", vehicle.rentalEnd.ifBlank { "未登録" })
                                            DetailRow("月額料金", vehicle.rentalMonthlyYen?.let(::yen) ?: "未登録")
                                        }
                                        DetailRow("状態", if (vehicle.active) "有効" else "無効")
                                        if (vehicle.note.isNotBlank()) Text(vehicle.note)
                                        OutlinedButton(onClick = { open(ScooterEditor.VEHICLE, vehicle.id) }) { Text("車両情報・写真を編集") }
                                    }
                                }
                                item {
                                    InfoCard {
                                        Text("${year}年のODO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        if (reading == null) Text("年始または利用開始時と、最終時点のODOを登録してください。") else {
                                            DetailRow("年始・利用開始", "${reading.startDate} / ${number(reading.startOdo)} km")
                                            DetailRow("最終・集計時点", "${reading.endDate} / ${number(reading.endOdo)} km")
                                        }
                                        OutlinedButton(onClick = { open(ScooterEditor.ANNUAL) }) { Text("年度別ODOを編集") }
                                    }
                                }
                            }
                            else -> {
                                val kind = if (page == 2) ExpenseKind.FUEL else ExpenseKind.MAINTENANCE
                                val records = expenses.filter { it.kind == kind && it.date.year == year }
                                item { InfoCard { DetailRow("${kind.label}費 合計", yen(records.sumOf { it.yen })); Text("${records.size}件 · タップして編集") } }
                                if (records.isEmpty()) item { Text("記録がありません。右下の＋から${kind.label}を登録できます。") }
                                items(records, key = { "expense-${it.id}" }) { e ->
                                    OutlinedCard(onClick = { open(if (page == 2) ScooterEditor.FUEL else ScooterEditor.MAINTENANCE, e.id) }, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            DetailRow(e.date.toString(), yen(e.yen))
                                            Text("${number(e.odo)} km" + (e.liters?.let { " · $it L" } ?: ""))
                                            if (e.description.isNotBlank()) Text(e.description)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showActions && vehicle != null) ModalBottomSheet(onDismissRequest = { showActions = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("登録・編集", style = MaterialTheme.typography.titleLarge)
            val ongoing = data.rides.find { it.vehicleId == vehicle.id && it.end == null }
            Button(onClick = { open(ScooterEditor.RIDE, ongoing?.id ?: 0) }, modifier = Modifier.fillMaxWidth()) { Text(if (ongoing == null) "乗車ODOを登録" else "降車ODOを登録") }
            OutlinedButton(onClick = { open(ScooterEditor.FUEL) }, modifier = Modifier.fillMaxWidth()) { Text("給油を登録") }
            OutlinedButton(onClick = { open(ScooterEditor.MAINTENANCE) }, modifier = Modifier.fillMaxWidth()) { Text("整備を登録") }
            TextButton(onClick = { open(ScooterEditor.ANNUAL) }) { Text("${year}年の基準ODOを編集") }
            TextButton(onClick = { open(ScooterEditor.VEHICLE, vehicle.id) }) { Text("車両情報・写真を編集") }
        }
    }
    editor?.takeIf { !loading && repository != null && (it == ScooterEditor.VEHICLE || vehicle != null) }?.let { type ->
        ScooterEditorSheet(type, editId, vehicle, year, data, repository!!,
            onDismiss = { editor = null },
            onSaved = { createdId ->
                editor = null
                if (createdId != null) selectedId = createdId
                scope.launch { load(); snackbar.showSnackbar("保存しました") }
            },
        )
    }
}

@Composable
internal fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1.2f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun number(value: Long): String = String.format(Locale.JAPAN, "%,d", value)
internal fun yen(value: Long): String = "¥${number(value)}"
internal fun dateTime(value: String): String = value.replace('T', ' ')

@Composable
private fun ScooterDashboard(year: Int, reading: ScooterAnnualReading?, rides: List<ScooterRide>, expenses: List<ScooterExpense>, onEditAnnual: () -> Unit) {
    val summary = ScooterCalculator.summarize(year, reading, rides)
    val purple = MaterialTheme.colorScheme.primary
    val gray = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard {
            Text("業務利用割合", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (summary.issue != null) {
                Text(summary.issue, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onEditAnnual) { Text("基準ODOを確認") }
                Text("記録済み業務距離 ${number(summary.businessKm)} km")
            } else {
                val percent = summary.businessPercent!!
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(112.dp)) {
                            val stroke = 18.dp.toPx()
                            drawArc(gray, -90f, 360f, false, style = Stroke(stroke))
                            drawArc(purple, -90f, (percent * 3.6).toFloat(), false, style = Stroke(stroke))
                        }
                        Text(String.format(Locale.JAPAN, "%.1f%%", percent), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("● 業務 ${String.format(Locale.JAPAN, "%.1f%%", percent)}", color = purple)
                        Text("○ 私用 ${String.format(Locale.JAPAN, "%.1f%%", 100 - percent)}")
                    }
                }
                HorizontalDivider()
                DetailRow("総走行距離", "${number(summary.totalKm!!)} km")
                DetailRow("業務走行距離", "${number(summary.businessKm)} km")
                DetailRow("私用走行距離", "${number(summary.privateKm!!)} km")
                Text("集計期間 ${reading!!.startDate}〜${reading.endDate}", style = MaterialTheme.typography.bodySmall)
            }
        }
        InfoCard {
            Text("月別の業務走行距離", fontWeight = FontWeight.Bold)
            val months = (1..12).map { month -> rides.filter { it.start.year == year && it.start.monthValue == month }.sumOf { it.distance ?: 0 } }
            val maximum = maxOf(1L, months.max())
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val unit = size.width / 12
                months.forEachIndexed { index, km ->
                    val height = size.height * km / maximum
                    drawRect(purple, topLeft = androidx.compose.ui.geometry.Offset(index * unit + unit * .15f, size.height - height), size = Size(unit * .7f, height))
                }
            }
            Row(Modifier.fillMaxWidth()) { (1..12).forEach { Text("$it", Modifier.weight(1f), fontSize = 10.sp) } }
            Text(months.mapIndexedNotNull { i, km -> if (km > 0) "${i + 1}月 ${number(km)} km" else null }.joinToString(" / ").ifBlank { "完了した業務走行はありません" }, style = MaterialTheme.typography.bodySmall)
        }
        InfoCard {
            Text("${year}年の費用", fontWeight = FontWeight.Bold)
            val annual = expenses.filter { it.date.year == year }
            DetailRow("給油費", yen(annual.filter { it.kind == ExpenseKind.FUEL }.sumOf { it.yen }))
            DetailRow("整備費", yen(annual.filter { it.kind == ExpenseKind.MAINTENANCE }.sumOf { it.yen }))
            DetailRow("合計", yen(annual.sumOf { it.yen }))
            Text("登録金額の合計（按分前）", style = MaterialTheme.typography.bodySmall)
        }
    }
}
