package com.example.monday
import com.example.monday.core.utils.*
import com.example.monday.data.models.TodoItem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DashedLine(color: Color, dashLength: Float, gapLength: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val totalDashWidth = dashLength + gapLength
        var currentX = 0f

        while (currentX < canvasWidth) {
            drawLine(
                color = color,
                start = Offset(currentX, 0f),
                end = Offset(currentX + dashLength, 0f),
                strokeWidth = 2f
            )
            currentX += totalDashWidth
        }
    }
}

@Composable
fun ExpensesList(todoItems: List<TodoItem>, totalSum: Double) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text(
            text = "....",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // weight(1f) gives the LazyColumn bounded height within this Column,
        // preventing the "infinity maximum height constraints" crash
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(
                items = todoItems,
                key = { _, item -> item.id } // Stable key prevents O(N) recomposition
            ) { index, item ->
                val (name, quantity, price) = parseItemText(item.text)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "${index + 1}. ",
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f)
                        )
                        if (quantity != null) {
                            Text(
                                text = quantity,
                                modifier = Modifier.width(60.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Red
                            )
                        }
                        Text(
                            text = "Rs $price",
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.End
                        )
                    }
                    DashedLine(
                        color = Color.Gray,
                        dashLength = 10f,
                        gapLength = 10f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Total: Rs $totalSum",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewExpensesList() {
    val sampleItems = listOf(
        TodoItem(
            text = "Item 1 (1kg) - Rs 100",
            isDone = true
        ),
        TodoItem(
            text = "Item 2 (500g) - Rs 50",
            isDone = false
        ),
        TodoItem(
            text = "Item 3 - Rs 200",
            isDone = true
        )
    )
    val totalSum = 350.0
    ExpensesList(todoItems = sampleItems, totalSum = totalSum)
}
