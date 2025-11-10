package com.example.monday

/**
 * Generates all possible combinations of elements from a list of groups.
 * For example, given [[a, b], [c, d]], it will produce [[a, c], [a, d], [b, c], [b, d]].
 */
fun getCombinations(groups: List<List<String>>): List<List<String>> {
    if (groups.isEmpty()) {
        return listOf(emptyList())
    }
    val firstGroup = groups.first()
    val restOfCombinations = getCombinations(groups.drop(1))
    return firstGroup.flatMap { element ->
        restOfCombinations.map { combo ->
            listOf(element) + combo
        }
    }
} 