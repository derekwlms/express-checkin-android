package com.writestreams.checkin.util

abstract class BaseLabel {
    abstract fun asTSPLCommand(): String
}

class Label(
    private val title: String,
    private val subtitle: String
) : BaseLabel() {
    override fun asTSPLCommand(): String {
        // TEXT X, Y, ”font”, rotation, x-multiplication, y-multiplication, “content”
        return """
            SIZE 59 mm,102 mm
            GAP 5mm,0
            CLS
            TEXT 200,800,"2",90,3,3,"$title"
            TEXT 275,800,"1",90,2,2,"$subtitle"
            PRINT 1
            END
        """.trimIndent()
    }
}

class ParentLabel(
    private val dateTime: String,
    private val parentName1: String,
    private val parentName2: String,
    private val checkinCode: String,
    private val childNames: List<String>
) : BaseLabel() {
    override fun asTSPLCommand(): String {
        return """
            SIZE 59 mm,102 mm
            GAP 5mm,0
            CLS
            TEXT 50,810,"1",90,2,2,"$parentName1"
            TEXT 100,810,"1",90,2,2,"$parentName2"
            TEXT 200,810,"2",90,3,3,"$checkinCode"
            TEXT 350,810,"1",90,2,2,"$dateTime",
            TEXT 50,380,"1",90,2,2,"$parentName1"
            TEXT 100,380,"1",90,2,2,"$parentName2"
            TEXT 200,380,"2",90,3,3,"$checkinCode"
            TEXT 350,380,"1",90,2,2,"$dateTime",            
            PRINT 1
            END
        """.trimIndent()
    }
}

class ChildLabel(
    private val dateTime: String,
    private val sequenceNumber: String,
    private val childName: String,
    private val parentPhone: String,
    private val checkinCode: String,
    private val parentNames: String
) : BaseLabel() {
    override fun asTSPLCommand(): String {
        return """
            SIZE 59 mm,102 mm
            GAP 5mm,0
            CLS
            TEXT 45,100,"1",90,2,2,"$sequenceNumber"
            TEXT 45,800,"1",90,2,2,"$dateTime"
            TEXT 200,800,"2",90,3,3,"$childName"
            TEXT 275,800,"1",90,2,2,"$parentPhone"
            TEXT 350,800,"1",90,2,2,"$parentNames"
            TEXT 400,150,"2",90,2,2,"$checkinCode"
            PRINT 1
            END
        """.trimIndent()
    }
}

class GuestLabel(
    private val dateTime: String,
    private val parentName: String,
    private val checkinCode: String,
    private val childNames: List<String>
) : BaseLabel() {
    override fun asTSPLCommand(): String {
        val childTextLines = childNames.mapIndexed { index, childName ->
            "TEXT ${50 + index * 50},300,\"1\",90,2,2,\"$childName\""
        }.joinToString("\n")
        return """
            SIZE 59 mm,102 mm
            GAP 5mm,0
            CLS
            TEXT 50,810,"1",90,2,2,"$parentName"
            TEXT 200,810,"2",90,3,3,"$checkinCode"
            TEXT 350,810,"1",90,2,2,"$dateTime",
            $childTextLines
            PRINT 1
            END
        """.trimIndent()
    }
}

class AttendanceLabel(
    private val dateTime: String,
    private val count: String,
    private val attendees: List<String>,
    private val isContinuation: Boolean = false
) : BaseLabel() {
    override fun asTSPLCommand(): String {
        val startY = if (isContinuation) 70 else 170
        val attendeeLines = attendees.mapIndexed { index, attendeeName ->
            "TEXT 45,${startY + index * 40},\"1\",0,2,2,\"$attendeeName\""
        }.joinToString("\n")
        var headerLines = ""
        if (!isContinuation) {
            headerLines = """
                TEXT 45,40,"1",0,2,2,"SGC Children's Check-in"
                TEXT 45,80,"1",0,2,2,"$dateTime"
                TEXT 45,120,"1",0,2,2,"Count: $count"
            """
        }
        return """
            SIZE 59 mm,102 mm
            GAP 5mm,0
            CLS
            $headerLines
            $attendeeLines
            PRINT 1
            END
        """.trimIndent()
    }
}

class ReferenceLabel(
) : BaseLabel() {
    override fun asTSPLCommand(): String {
        // TEXT X, Y, ”font”, rotation, x-multiplication, y-multiplication, “content”
        return """
            SIZE 59 mm,102 mm
            GAP 5mm,0
            CLS
            TEXT 50,80,"1",90,1,1,"50,80,1"
            TEXT 120,140,"1",90,1,1,"120,140,1"
            TEXT 200,200,"2",90,1,1,"X |      Y <"            
            TEXT 250,250,"2",90,2,2,"250,250,2"
            TEXT 350,600,"2",90,2,2,"350,600,2",
            TEXT 440,810,"1",90,1,1,"440,810,1",            
            PRINT 1
            END
        """.trimIndent()
    }
}