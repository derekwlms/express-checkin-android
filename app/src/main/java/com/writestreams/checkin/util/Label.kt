package com.writestreams.checkin.util

open class Label(
    val time: String,
    val number: String,
    val name: String,
    val phone: String,
    val id: String,
    val image: String
) {
    open fun asFPSLCommand(): String {
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

// TODO Finish parent and attendance label formatting

class ParentLabel(
    time: String,
    number: String,
    name: String,
    phone: String,
    id: String,
    image: String
) : Label(time, number, name, phone, id, image) {

    override fun asFPSLCommand(): String {
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

class ChildLabel(
    time: String,
    number: String,
    name: String,
    phone: String,
    id: String,
    image: String
) : Label(time, number, name, phone, id, image)

class AttendanceLabel(
    time: String,
    number: String,
    name: String,
    phone: String,
    id: String,
    image: String
) : Label(time, number, name, phone, id, image) {

    override fun asFPSLCommand(): String {
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