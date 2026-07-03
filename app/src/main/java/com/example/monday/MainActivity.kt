package com.example.monday

import androidx.hilt.navigation.compose.hiltViewModel
import com.example.monday.viewmodels.MainViewModel
import com.example.monday.viewmodels.SettingsViewModel
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.monday.ui.theme.KharchajiTheme
import com.example.monday.ui.overlay.OverlayHelper
import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import com.example.monday.ModernExpenseListScreen
import com.example.monday.StatisticsScreen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import com.example.monday.core.navigation.*
import java.time.LocalDate
import com.example.monday.ui.screens.BatchSaveScreen
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import com.example.monday.ui.screens.SettingsScreen
import com.example.monday.AllExpensesScreen
import com.example.monday.FindAndReplaceScreen
import com.example.monday.MonthlyReportScreen
import com.example.monday.CategoryFilterScreen
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.monday.ui.screens.MasterOnlyCategoriesScreen
import com.example.monday.ui.screens.MasterCategoryDetailScreen
import com.example.monday.ui.modern.ModernColors

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure edge-to-edge behavior to use the full mobile screen size
        enableEdgeToEdge()
        
        // Auto-start overlay service
        OverlayHelper.startOverlayService(this)
        
        setContent {
            KharchajiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TodoApp()
                }
            }
        }
    }
}



@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(
    todoViewModel: TodoViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    exportViewModel: com.example.monday.viewmodels.ExportViewModel = hiltViewModel(),
    statsViewModel: com.example.monday.viewmodels.StatsViewModel = hiltViewModel()
) {
    val topLevelRoutes = setOf(ExpenseListRoute, StatisticsRoute, ShareScreenRoute, SettingsScreenRoute)
    val navigationState = rememberNavigationState(startRoute = ExpenseListRoute, topLevelRoutes = topLevelRoutes)
    val navigator = Navigator(navigationState)
    val currentRoute = navigationState.topLevelRoute

    val myEntryProvider = entryProvider {
        entry<ExpenseListRoute> {
            ModernExpenseListScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
exportViewModel = exportViewModel,
                statsViewModel = statsViewModel,
                settingsViewModel = settingsViewModel,
                onShareClick = {
                    navigator.navigate(ShareScreenRoute)
                },
                onNavigateToBatchSave = { navigator.navigate(BatchSaveScreenRoute) },
                onNavigateToSettings = { navigator.navigate(SettingsScreenRoute) },
                onViewRecordsClick = { 
                    val currentSelectedDateMillis = mainViewModel.selectedDate.value.toEpochMilli()
                    navigator.navigate(CalculationRecordsRoute(currentSelectedDateMillis))
                },
                onAllExpensesClick = {
                    navigator.navigate(AllExpensesRoute)
                }
            )
        }
        entry<AddExpenseRoute> {
            AddNewExpenseScreenV2(
                onNextClick = { navigator.goBack() },
                todoViewModel = todoViewModel
            )
        }
        entry<StatisticsRoute> {
            StatisticsScreen(
                onNavigateToAllExpenses = {
                    navigator.navigate(AllExpensesRoute)
                },
                onNavigateToFindAndReplace = {
                    navigator.navigate(FindAndReplaceRoute)
                },
                onNavigateToMonthlyReport = {
                    navigator.navigate(MonthlyReportRoute)
                },
                onNavigateToCategories = {
                    navigator.navigate(MasterOnlyCategoriesRoute)
                },
                onNavigateToTrends = {
                    navigator.navigate(TrendsRoute)
                }
            )
        }
        entry<ShareScreenRoute> {
            val currentSelectedDate by mainViewModel.selectedDate.collectAsState()
            ShareScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
                statsViewModel = statsViewModel,
                currentSelectedDate = currentSelectedDate,
                onDismiss = { navigator.goBack() }
            )
        }
        entry<CalculationRecordsRoute> { key ->
            val recordDate: LocalDate? = if (key.dateMillis == 0L) null else Instant.ofEpochMilli(key.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            
            CalculationRecordsScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
statsViewModel = statsViewModel,
                displayDate = recordDate,
                onNavigateBack = { navigator.goBack() },
                onRecordClick = { recordId ->
                    navigator.navigate(CalculationRecordDetailRoute(recordId))
                },
                onEditRecordClick = { recordId ->
                    navigator.navigate(EditRecordDetailRoute(recordId))
                }
            )
        }
        entry<CalculationRecordDetailRoute> { key ->
            CalculationRecordDetailScreen(
                recordId = key.recordId,
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() },
                onSetMemoAndReturnToExpenses = {
                    navigator.navigate(ExpenseListRoute)
                }
            )
        }
        entry<EditRecordDetailRoute> { key ->
            EditRecordScreen(
                recordId = key.recordId,
                todoViewModel = todoViewModel,
                statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() },
                onSaveComplete = { navigator.goBack() }
            )
        }
        entry<BatchSaveScreenRoute> {
            BatchSaveScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
                statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<SettingsScreenRoute> {
            SettingsScreen(
                viewModel = todoViewModel,
                mainViewModel = mainViewModel,
                statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<AllExpensesRoute> {
            AllExpensesScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
                statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<TrendsRoute> {
            TrendsScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
onNavigateBack = { navigator.goBack() }
            )
        }
        entry<FindAndReplaceRoute> {
            FindAndReplaceScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<MonthlyReportRoute> {
            MonthlyReportScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() },
                onNavigateToFilter = {
                    navigator.navigate(CategoryFilterRoute)
                },
                selectedCategories = emptyList() 
            )
        }
        entry<CategoryFilterRoute> {
            CategoryFilterScreen(
                todoViewModel = todoViewModel,
                mainViewModel = mainViewModel,
                onNavigateBack = { navigator.goBack() },
                initialSelectedCategories = emptyList(),
                onApplyFilters = { filters ->
                    navigator.goBack()
                }
            )
        }
        entry<MasterOnlyCategoriesRoute> {
            MasterOnlyCategoriesScreen(
                viewModel = todoViewModel,
                mainViewModel = mainViewModel,
                onNavigateBack = { navigator.goBack() },
                onCategoryClick = { category ->
                    navigator.navigate(MasterCategoryDetailRoute(category))
                }
            )
        }
        entry<MasterCategoryDetailRoute> { key ->
            MasterCategoryDetailScreen(
                viewModel = todoViewModel,
                category = key.category,
                onNavigateBack = { navigator.goBack() }
            )
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute !is AddExpenseRoute &&
                currentRoute !is ShareScreenRoute &&
                currentRoute !is CalculationRecordDetailRoute &&
                currentRoute !is EditRecordDetailRoute
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Gradient divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            ModernColors.EggnogLight.copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        // Nav bar
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape,
                            color = ModernColors.SoftCream,
                            shadowElevation = 0.dp
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Home
                                NavigationBarItem(
                                    selected = currentRoute is ExpenseListRoute,
                                    onClick = {
                                        if (currentRoute !is ExpenseListRoute)
                                            navigator.navigate(ExpenseListRoute)
                                    },
                                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home", modifier = Modifier.size(22.dp)) },
                                    label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF222222),
                                        selectedTextColor = Color(0xFF222222),
                                        unselectedIconColor = Color(0xFF999999),
                                        unselectedTextColor = Color(0xFF999999),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                // Insights
                                NavigationBarItem(
                                    selected = currentRoute is StatisticsRoute,
                                    onClick = { navigator.navigate(StatisticsRoute) },
                                    icon = { Icon(Icons.Filled.Assessment, contentDescription = "Insights", modifier = Modifier.size(22.dp)) },
                                    label = { Text("Insights", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF222222),
                                        selectedTextColor = Color(0xFF222222),
                                        unselectedIconColor = Color(0xFF999999),
                                        unselectedTextColor = Color(0xFF999999),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                // Export
                                val exportCount by exportViewModel.exportBufferCount.collectAsState()
                                NavigationBarItem(
                                    selected = currentRoute is ShareScreenRoute,
                                    onClick = { navigator.navigate(ShareScreenRoute) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (exportCount > 0) {
                                                    Badge(containerColor = Color.Red, contentColor = Color.White) {
                                                        Text("$exportCount")
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = "Export", modifier = Modifier.size(22.dp))
                                        }
                                    },
                                    label = { Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF222222),
                                        selectedTextColor = Color(0xFF222222),
                                        unselectedIconColor = Color(0xFF999999),
                                        unselectedTextColor = Color(0xFF999999),
                                        indicatorColor = Color.Transparent
                                    )
                                )

                                // Settings
                                NavigationBarItem(
                                    selected = currentRoute is SettingsScreenRoute,
                                    onClick = { navigator.navigate(SettingsScreenRoute) },
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(22.dp)) },
                                    label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF222222),
                                        selectedTextColor = Color(0xFF222222),
                                        unselectedIconColor = Color(0xFF999999),
                                        unselectedTextColor = Color(0xFF999999),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }

                    // Floating "+" FAB
                    FloatingActionButton(
                        onClick = { navigator.navigate(AddExpenseRoute) },
                        containerColor = Color(0xFF222222),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 18.dp)
                            .offset(y = (-44).dp)
                            .size(52.dp),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 10.dp,
                            pressedElevation = 16.dp
                        )
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

    ) { innerPadding ->
        NavDisplay(
            entries = navigationState.toEntries(myEntryProvider),
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            onBack = { navigator.goBack() },
            sceneStrategy = DialogSceneStrategy()
        )
    }
}
@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    KharchajiTheme {
        TodoApp()
    }
}
