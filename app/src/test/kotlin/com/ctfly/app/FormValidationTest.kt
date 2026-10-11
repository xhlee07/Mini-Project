package com.ctfly.app

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class FormValidationTest {
    private val now=Instant.parse("2026-10-11T02:35:40Z").toEpochMilli()
    @Test fun bookingUsesCurrentMinuteAndRejectsEachWrongField() {
        assertNull(bookingInputError("2026-10-11","10:35",30,1,1,now))
        assertTrue(bookingInputError("2026-10-10","10:35",30,1,1,now)!!.contains("date"))
        assertTrue(bookingInputError("2026-10-11","10:34",30,1,1,now)!!.contains("time"))
        assertTrue(bookingInputError("2026-10-11","10:35",15,1,1,now)!!.contains("30"))
        assertTrue(bookingInputError("2026-10-11","10:35",30,2,1,now)!!.contains("Players"))
    }
    @Test fun moneyRequiresTwoDecimalPlacesAndNoSilentRounding() {
        assertEquals(550,moneyInputSen("5.50"));assertEquals(0,moneyInputSen("0"))
        assertNull(moneyInputSen("5.555"));assertNull(moneyInputSen("-1"));assertNull(moneyInputSen("99999999"))
    }
    @Test fun formReportsEveryRequiredAndMalformedField() {
        val fields=listOf(FormField("name","Competition name"),FormField("fee_rm","Entry fee (RM)","money"),FormField("capacity","Team slots","number"),FormField("description","Details","multiline",optional=true))
        val errors=validateFormFields("save_event",mapOf("name" to "","fee_rm" to "bad","capacity" to "65"),fields)
        assertEquals(3,errors.size);assertTrue(errors.any{it.contains("Competition name")});assertTrue(errors.any{it.contains("Entry fee")});assertTrue(errors.any{it.contains("Team slots")})
    }
}
