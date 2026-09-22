// QluCampus 0.2.0, GPL-3.0.
package com.dawncourse.core.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MondayPicker(initial: LocalDate?, academicYear: Int? = null, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val allowed = remember(academicYear) { object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
            return d.dayOfWeek == DayOfWeek.MONDAY && (academicYear == null || d.year in academicYear..academicYear + 1)
        }
    } }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialDisplayedMonthMillis = (initial ?: academicYear?.let { LocalDate.of(it, 9, 1) } ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = academicYear?.let { it..it + 1 } ?: 2000..2101,
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = allowed
    )
    DawnDatePickerDialog(state, onDismiss, {
        state.selectedDateMillis?.takeIf(allowed::isSelectableDate)?.let {
            onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }, "选择第一教学周的周一", confirmEnabled = state.selectedDateMillis?.let(allowed::isSelectableDate) == true)
}
