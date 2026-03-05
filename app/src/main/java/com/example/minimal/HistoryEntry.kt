package com.example.minimal

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(
    val drawDate: String,                  // "MM/dd/yyyy"
    val winningWhite: Set<Int>,
    val winningPowerball: Int?,
    val userWhite: Set<Int>,               // ← new: what user had selected then
    val userPowerball: Int?,               // ← new
    val note: String = ""                  // ← new: user-added note, default empty
)