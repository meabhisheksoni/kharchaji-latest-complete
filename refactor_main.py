import re

with open("app/src/main/java/com/example/monday/MainActivity.kt", "r", encoding="utf-8") as f:
    content = f.read()

parts = content.split("@RequiresApi(Build.VERSION_CODES.O)\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun TodoApp(")
before = parts[0]

after_parts = parts[1].split("@RequiresApi(Build.VERSION_CODES.O)\n@Preview(showBackground = true)\n@Composable\nfun DefaultPreview()")
after = "@RequiresApi(Build.VERSION_CODES.O)\n@Preview(showBackground = true)\n@Composable\nfun DefaultPreview()" + after_parts[1]

todo_app_new = """@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(
    todoViewModel: TodoViewModel = hiltViewModel(),
    expenseViewModel: ExpenseViewModel = hiltViewModel(),
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
                expenseViewModel = expenseViewModel,
                exportViewModel = exportViewModel,
                statsViewModel = statsViewModel,
                settingsViewModel = settingsViewModel,
                onShareClick = {
                    navigator.navigate(ShareScreenRoute)
                },
                onNavigateToBatchSave = { navigator.navigate(BatchSaveScreenRoute) },
                onNavigateToSettings = { navigator.navigate(SettingsScreenRoute) },
                onViewRecordsClick = { 
                    val currentSelectedDateMillis = todoViewModel.selectedDate.value.toEpochMilli()
                    navigator.navigate(CalculationRecordsRoute(currentSelectedDateMillis))
                },
                onAllExpensesClick = {
                    navigator.navigate(AllExpensesRoute)
                }
            )
        }
        entry<AddExpenseRoute> {
            AddNewExpenseScreenV2(
                expenseViewModel = expenseViewModel,
                onNextClick = { navigator.goBack() }
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
        entry<ShareScreenRoute>(DialogSceneStrategy) {
            val currentSelectedDate by todoViewModel.selectedDate.collectAsState()
            ShareScreen(
                todoViewModel = todoViewModel,
                expenseViewModel = expenseViewModel,
                exportViewModel = exportViewModel,
                statsViewModel = statsViewModel,
                currentSelectedDate = currentSelectedDate,
                onDismiss = { navigator.goBack() }
            )
        }
        entry<CalculationRecordsRoute> { key ->
            val recordDate: LocalDate? = if (key.dateMillis == 0L) null else Instant.ofEpochMilli(key.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            
            CalculationRecordsScreen(
                todoViewModel = todoViewModel,
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
                statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<SettingsScreenRoute> {
            SettingsScreen(
                viewModel = todoViewModel,
                statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<AllExpensesRoute> {
            AllExpensesScreen(
                todoViewModel = todoViewModel,
                statsViewModel = statsViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<TrendsRoute> {
            TrendsScreen(
                todoViewModel = todoViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<FindAndReplaceRoute> {
            FindAndReplaceScreen(
                todoViewModel = todoViewModel,
                onNavigateBack = { navigator.goBack() }
            )
        }
        entry<MonthlyReportRoute> {
            MonthlyReportScreen(
                todoViewModel = todoViewModel,
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
            backstacks = navigationState.toEntries(myEntryProvider),
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            onBack = { navigator.goBack() }
        )
    }
}
"""

new_content = before + todo_app_new + after

with open("app/src/main/java/com/example/monday/MainActivity.kt", "w", encoding="utf-8") as f:
    f.write(new_content)

print("Done")
