package com.bebecup.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.domain.profile.BabyAge
import com.bebecup.app.domain.profile.CalendarDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Asks for the baby's name (optional) and birthday. [onSave] gets the trimmed
 * name and the birthday as "yyyy-MM-dd"; it is only enabled once a birthday is
 * chosen, since the birthday is what the ages need.
 */
@Composable
fun BabyProfileDialog(
    initialName: String,
    initialBirthDate: String,
    onSave: (name: String, birthDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var birth by remember { mutableStateOf(CalendarDate.parse(initialBirthDate)) }
    var pickingBirth by remember { mutableStateOf(false) }

    if (pickingBirth) {
        BirthDatePickerDialog(
            initial = birth,
            onPick = { birth = it; pickingBirth = false },
            onDismiss = { pickingBirth = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("아기 정보") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("이름이나 태명 (선택)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("baby_name_field")
                )
                OutlinedButton(
                    onClick = { pickingBirth = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("baby_birth_btn")
                ) {
                    Text(birth?.let { "생일 ${it.year}년 ${it.month}월 ${it.day}일" } ?: "생일 고르기")
                }
                Text(
                    "이 기기 안에만 저장돼요. 사진마다 몇 개월 때 사진인지 보여 드려요.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { birth?.let { onSave(name.trim(), it.toString()) } },
                enabled = birth != null,
                modifier = Modifier.testTag("baby_profile_save_btn")
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/**
 * The app-themed (Material 3) calendar for the birthday. Days after today
 * cannot be picked. The picker speaks UTC midnight millis; [CalendarDate]
 * converts through epoch days, so no time zone can shift the date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(
    initial: CalendarDate?,
    onPick: (CalendarDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { CalendarDate.parse(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())) }
    val lastDayMillis = (today?.epochDay ?: Long.MAX_VALUE / DAY_MILLIS) * DAY_MILLIS
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { it.epochDay * DAY_MILLIS },
        yearRange = 1900..(today?.year ?: 2100),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= lastDayMillis
            override fun isSelectableYear(year: Int): Boolean = today == null || year <= today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(CalendarDate.fromEpochDay(Math.floorDiv(it, DAY_MILLIS))) }
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("baby_birth_confirm_btn")
            ) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    ) {
        DatePicker(state = state, modifier = Modifier.testTag("baby_birth_picker"))
    }
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

/** A small "4개월" label laid over a photo. */
@Composable
fun AgeBadge(age: BabyAge, modifier: Modifier = Modifier) {
    Text(
        age.shortLabel,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .padding(6.dp)
            .background(Color(0x99000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("age_badge")
    )
}
