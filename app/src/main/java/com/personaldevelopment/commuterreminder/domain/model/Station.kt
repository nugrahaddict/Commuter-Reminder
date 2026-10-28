package com.personaldevelopment.commuterreminder.domain.model

import java.io.Serializable

data class Station(
    val id: String,
    val name: String,
    val line: String,
    val orderIndex: Int,
    val latitude: Double,
    val longitude: Double
) : Serializable {

    val lineName: String
        get() = when (line) {
            "BOGOR" -> "Bogor Line (Jakarta Kota–Bogor)"
            "NAMBO" -> "Bogor Line cabang Nambo (Citayam–Nambo)"
            "CIKARANG" -> "Cikarang Loop Line (via Sudirman)"
            "CIKARANG_UTARA" -> "Cikarang Loop Line (via Pasar Senen)"
            "RANGKAS" -> "Rangkasbitung Line (Tanah Abang–Rangkasbitung)"
            "TANGERANG" -> "Tangerang Line (Duri–Tangerang)"
            "PRIOK" -> "Tanjung Priok Line (Jakarta Kota–Tanjung Priuk)"
            else -> line
        }

    val lineShortName: String
        get() = when (line) {
            "BOGOR" -> "Bogor"
            "NAMBO" -> "Nambo"
            "CIKARANG" -> "Cikarang Loop"
            "CIKARANG_UTARA" -> "Cikarang Utara"
            "RANGKAS" -> "Rangkasbitung"
            "TANGERANG" -> "Tangerang"
            "PRIOK" -> "Tanjung Priok"
            else -> line
        }
}
