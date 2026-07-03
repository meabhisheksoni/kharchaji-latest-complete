package com.example.monday.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object SplashRoute : NavKey
@Serializable data object ExpenseListRoute : NavKey
@Serializable data object AddExpenseRoute : NavKey
@Serializable data object StatisticsRoute : NavKey
@Serializable data object ShareScreenRoute : NavKey
@Serializable data class CalculationRecordsRoute(val dateMillis: Long) : NavKey
@Serializable data class CalculationRecordDetailRoute(val recordId: Int) : NavKey
@Serializable data class EditRecordDetailRoute(val recordId: Int) : NavKey
@Serializable data object BatchSaveScreenRoute : NavKey
@Serializable data object SettingsScreenRoute : NavKey
@Serializable data object AllExpensesRoute : NavKey
@Serializable data object FindAndReplaceRoute : NavKey
@Serializable data object MonthlyReportRoute : NavKey
@Serializable data object CategoryFilterRoute : NavKey
@Serializable data object MasterOnlyCategoriesRoute : NavKey
@Serializable data class MasterCategoryDetailRoute(val category: String) : NavKey
@Serializable data object TrendsRoute : NavKey
