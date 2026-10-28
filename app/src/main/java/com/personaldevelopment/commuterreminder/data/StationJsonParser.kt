package com.personaldevelopment.commuterreminder.data

import com.personaldevelopment.commuterreminder.data.local.StationEntity
import org.json.JSONArray

object StationJsonParser {

    fun parse(json: String): List<StationEntity> {
        val array = JSONArray(json)
        val stations = ArrayList<StationEntity>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            stations.add(
                StationEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    line = obj.getString("line"),
                    orderIndex = obj.getInt("orderIndex"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude")
                )
            )
        }
        return stations
    }
}
