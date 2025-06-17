package com.example.monday

import java.util.UUID
 
data class ExpenseInput(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    var price: String = ""
) 