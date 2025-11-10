package com.example.monday

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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.monday.ui.theme.KharchajiTheme
import com.example.monday.shareExpensesList
import com.example.monday.MainScreen
import com.example.monday.DedicatedExpenseListScreen
import com.example.monday.ShareScreen
import com.example.monday.StatisticsScreen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import java.time.LocalDate
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
import com.example.monday.ui.screens.MasterOnlyCategoriesScreen
import com.example.monday.ui.screens.MasterCategoryDetailScreen

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

// Define navigation routes
object AppDestinations {
    const val EXPENSE_LIST = "expenselist"
    const val ADD_EXPENSE = "addexpense"
    const val STATISTICS = "statistics"
    const val SHARE_SCREEN = "sharescreen"
    const val CALCULATION_RECORDS_BASE = "calculationrecords"
    const val CALCULATION_RECORDS_ROUTE = "calculationrecords/{dateMillis}"
    fun calculationRecordsRoute(dateMillis: Long) = "$CALCULATION_RECORDS_BASE/$dateMillis"
    const val CALCULATION_RECORD_DETAIL = "calculationrecorddetail"
    const val EDIT_RECORD_DETAIL = "editrecorddetail"
    const val BATCH_SAVE_SCREEN = "batchsavescreen"
    const val SETTINGS_SCREEN = "settings"
    const val ALL_EXPENSES = "allexpenses"
    const val FIND_AND_REPLACE = "findandreplace"
    const val MONTHLY_REPORT = "monthly_report"
    const val CATEGORY_FILTER = "category_filter"
    const val SPLASH = "splash"
    const val MASTER_ONLY_CATEGORIES = "master_only_categories"
    const val MASTER_CATEGORY_DETAIL = "master_category_detail/{category}"
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(todoViewModel: TodoViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != AppDestinations.ADD_EXPENSE &&
                currentRoute != AppDestinations.SHARE_SCREEN &&
                currentRoute != AppDestinations.CALCULATION_RECORD_DETAIL &&
                !currentRoute.toString().startsWith(AppDestinations.EDIT_RECORD_DETAIL)
            ) {
                BottomAppBar(
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    actions = {
                        IconButton(onClick = { navController.navigate(AppDestinations.EXPENSE_LIST) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "Expense List",
                                tint = if (currentRoute == AppDestinations.EXPENSE_LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { navController.navigate(AppDestinations.STATISTICS) }) {
                            Icon(
                                Icons.Filled.Assessment,
                                contentDescription = "Statistics",
                                tint = if (currentRoute == AppDestinations.STATISTICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.width(48.dp))
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { navController.navigate(AppDestinations.ADD_EXPENSE) },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Filled.Add, "Add new expense")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestinations.EXPENSE_LIST,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestinations.EXPENSE_LIST) {
                DedicatedExpenseListScreen(
                    todoViewModel = todoViewModel,
                    onShareClick = {
                        navController.navigate(AppDestinations.SHARE_SCREEN)
                    },
                    onNavigateToBatchSave = { navController.navigate(AppDestinations.BATCH_SAVE_SCREEN) },
                    onNavigateToSettings = { navController.navigate(AppDestinations.SETTINGS_SCREEN) },
                    onViewRecordsClick = { 
                        val currentSelectedDateMillis = todoViewModel.selectedDate.value.toEpochMilli()
                        Log.d("NavigationDebug", "Navigating to CalculationRecords with dateMillis: $currentSelectedDateMillis")
                        val route = AppDestinations.calculationRecordsRoute(currentSelectedDateMillis)
                        Log.d("NavigationDebug", "Attempting route: $route")
                        navController.navigate(route)
                    },
                    onAllExpensesClick = {
                        navController.navigate(AppDestinations.ALL_EXPENSES)
                    }
                )
            }
            composable(AppDestinations.ADD_EXPENSE) {
                Log.d("MainActivity", "Loading AddNewExpenseScreenV2")
                AddNewExpenseScreenV2(
                    todoViewModel = todoViewModel,
                    onNextClick = { navController.popBackStack() }
                )
            }
            composable(AppDestinations.STATISTICS) {
                StatisticsScreen(
                    onNavigateToAllExpenses = {
                        navController.navigate(AppDestinations.ALL_EXPENSES)
                    },
                    onNavigateToFindAndReplace = {
                        navController.navigate(AppDestinations.FIND_AND_REPLACE)
                    },
                    onNavigateToMonthlyReport = {
                        navController.navigate(AppDestinations.MONTHLY_REPORT)
                    },
                    onNavigateToCategories = {
                        navController.navigate(AppDestinations.MASTER_ONLY_CATEGORIES)
                    }
                )
            }
            composable(AppDestinations.SHARE_SCREEN) {
                val currentSelectedDate by todoViewModel.selectedDate.collectAsState()
                ShareScreen(
                    todoViewModel = todoViewModel,
                    currentSelectedDate = currentSelectedDate,
                    onDismiss = { navController.popBackStack() }
                )
            }
            composable(
                route = AppDestinations.CALCULATION_RECORDS_ROUTE,
                arguments = listOf(navArgument("dateMillis") { type = NavType.LongType })
            ) { backStackEntry ->
                Log.d("NavigationDebug", "Entered CALCULATION_RECORDS_ROUTE composable")
                val dateMillisFromArgs = backStackEntry.arguments?.getLong("dateMillis")
                Log.d("NavigationDebug", "dateMillis from args: $dateMillisFromArgs")

                val recordDate: LocalDate? = dateMillisFromArgs?.let { millis -> 
                    if (millis == 0L && backStackEntry.arguments?.containsKey("dateMillis") == false) {
                        Log.d("NavigationDebug", "dateMillis was 0L and key was missing, treating as null.")
                        null
                    } else {
                        Log.d("NavigationDebug", "Converting millis $millis to LocalDate")
                        millis.toLocalDate()
                    }
                }
                Log.d("NavigationDebug", "Final recordDate: $recordDate")
                
                CalculationRecordsScreen(
                    todoViewModel = todoViewModel,
                    displayDate = recordDate,
                    onNavigateBack = { navController.popBackStack() },
                    onRecordClick = { recordId ->
                        navController.navigate("${AppDestinations.CALCULATION_RECORD_DETAIL}/$recordId")
                    },
                    onEditRecordClick = { recordId ->
                        navController.navigate("${AppDestinations.EDIT_RECORD_DETAIL}/$recordId")
                    }
                )
            }
            composable("${AppDestinations.CALCULATION_RECORD_DETAIL}/{recordId}") { backStackEntry ->
                val recordId = backStackEntry.arguments?.getString("recordId")?.toIntOrNull()
                if (recordId != null) {
                    CalculationRecordDetailScreen(
                        recordId = recordId,
                        todoViewModel = todoViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onSetMemoAndReturnToExpenses = {
                            navController.popBackStack(AppDestinations.EXPENSE_LIST, inclusive = false)
                        }
                    )
                } else {
                    navController.popBackStack()
                }
            }
            composable("${AppDestinations.EDIT_RECORD_DETAIL}/{recordId}") { backStackEntry ->
                val recordId = backStackEntry.arguments?.getString("recordId")?.toIntOrNull()
                if (recordId != null) {
                    EditRecordScreen(
                        recordId = recordId,
                        todoViewModel = todoViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onSaveComplete = { navController.popBackStack() }
                    )
                } else {
                    navController.popBackStack()
                }
            }
            
            composable(AppDestinations.BATCH_SAVE_SCREEN) {
                BatchSaveScreen(
                    todoViewModel = todoViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(AppDestinations.SETTINGS_SCREEN) {
                SettingsScreen(
                    viewModel = todoViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(AppDestinations.ALL_EXPENSES) {
                AllExpensesScreen(
                    todoViewModel = todoViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(AppDestinations.FIND_AND_REPLACE) {
                FindAndReplaceScreen(
                    todoViewModel = todoViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(AppDestinations.MONTHLY_REPORT) {
                val selectedCategories by navController
                    .currentBackStackEntry!!
                    .savedStateHandle
                    .getLiveData<List<String>>("selected_categories")
                    .observeAsState(emptyList())

                MonthlyReportScreen(
                    todoViewModel = todoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFilter = {
                        navController.navigate(AppDestinations.CATEGORY_FILTER)
                    },
                    selectedCategories = selectedCategories
                )
            }
            composable(AppDestinations.CATEGORY_FILTER) {
                val initialCategories = navController
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<List<String>>("selected_categories") ?: emptyList()
                
                CategoryFilterScreen(
                    todoViewModel = todoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    initialSelectedCategories = initialCategories,
                    onApplyFilters = { filters ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("selected_categories", filters)
                        navController.popBackStack()
                    }
                )
            }
            composable(AppDestinations.MASTER_ONLY_CATEGORIES) {
                MasterOnlyCategoriesScreen(
                    viewModel = todoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onCategoryClick = { category ->
                        navController.navigate("${AppDestinations.MASTER_CATEGORY_DETAIL}/$category")
                    }
                )
            }

            composable(AppDestinations.MASTER_CATEGORY_DETAIL) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category") ?: ""
                MasterCategoryDetailScreen(
                    viewModel = todoViewModel,
                    category = category,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
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