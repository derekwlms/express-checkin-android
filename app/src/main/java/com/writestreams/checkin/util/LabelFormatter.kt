package com.writestreams.checkin.util

object LabelFormatter {
    fun generateFPSLCommand(
        time: String,
        number: String,
        name: String,
        phone: String,
        id: String,
        image: String
    ): String {
        return """
            SIZE 59 mm,102 mm
            GAP 5mm,0
            CLS
            TEXT 45,100,"1",90,2,2,"$number"
            TEXT 45,800,"1",90,2,2,"$time"
            TEXT 200,850,"1",90,2,2,"$image"
            TEXT 200,630,"2",90,3,3,"$name"
            TEXT 275,630,"1",90,2,2,"$phone"
            TEXT 400,150,"2",90,2,2,"$id"
            PRINT 1
            END
        """.trimIndent()
    }
}