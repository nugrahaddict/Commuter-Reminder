package com.personaldevelopment.commuterreminder.domain.model

data class Station(
    val id: String,
    val name: String,
    val line: String,
    val orderIndex: Int,
    val latitude: Double,
    val longitude: Double
)
