package com.example.monday.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.monday.TodoViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterOnlyCategoriesScreen(
    viewModel: TodoViewModel,
    onNavigateBack: () -> Unit,
    onCategoryClick: (String) -> Unit
) {
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.getMasterOnlyCategories().collectLatest { cats ->
            categories = cats.sorted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Master-only Categories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No master-only categories found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(categories) { category ->
                    ListItem(
                        headlineContent = { Text(category) },
                        supportingContent = { Text("Tap to view expenses") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategoryClick(category) }
                            .padding(horizontal = 8.dp),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    Divider()
                }
            }
        }
    }
} 