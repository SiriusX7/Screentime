package com.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screentime.ui.theme.ScreentimeTheme

@Composable
fun ScreenTimeScreen(
    modifier: Modifier = Modifier,
    onSetLimitClick: () -> Unit = {}
) {
    // Colors based on the Figma design
    val textColor = Color(0xFF1A1A1A)
    val lightGray = Color(0xFF888888)
    val borderColor = Color(0xFFD6D6D6)
    val primaryPurple = Color(0xFF6D00DF)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp)
    ) {
        // Main Title
        Text(
            text = "Screen Time",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            letterSpacing = (-1).sp
        )

        // Divider Line
        HorizontalDivider(
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            thickness = 1.dp,
            color = borderColor
        )

        // Subtitle
        Text(
            text = "Set your screen time",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        val timerState by TimerManager.timerState.collectAsState()
        val timeRemaining by TimerManager.timeRemaining.collectAsState()
        
        var selectedHours by remember { mutableStateOf("00") }
        var selectedMinutes by remember { mutableStateOf("00") }

        // Section Label
        Text(
            text = if (timerState == TimerState.IDLE) "Daily Limit" else "Remaining Time",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (timerState == TimerState.IDLE) {
            // Pickers Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hours Column
                val hourValues = remember { (0..23).map { it.toString().padStart(2, '0') } }
                TimePickerBox(
                    label = "Hours",
                    values = hourValues,
                    initialIndex = 0, // Default to "00"
                    onValueChange = { selectedHours = it },
                    modifier = Modifier.weight(1f),
                    textColor = textColor,
                    lightGray = lightGray,
                    borderColor = borderColor
                )

                // Minutes Column
                val minuteValues = remember { (0..59).step(5).map { it.toString().padStart(2, '0') } }
                TimePickerBox(
                    label = "Minutes",
                    values = minuteValues,
                    initialIndex = 0, // Default to "00"
                    onValueChange = { selectedMinutes = it },
                    modifier = Modifier.weight(1f),
                    textColor = textColor,
                    lightGray = lightGray,
                    borderColor = borderColor
                )
            }
        } else {
            // Display remaining time
            val hours = (timeRemaining / (1000 * 60 * 60))
            val minutes = (timeRemaining / (1000 * 60)) % 60
            val seconds = (timeRemaining / 1000) % 60
            val timeString = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = timeString,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        val context = androidx.compose.ui.platform.LocalContext.current

        if (timerState == TimerState.IDLE) {
            // Set Limit Button
            Button(
                onClick = {
                    val totalMs = (selectedHours.toLong() * 60 * 60 * 1000) + (selectedMinutes.toLong() * 60 * 1000)
                    if (totalMs > 0) {
                        val intent = android.content.Intent(context, ScreenTimeService::class.java).apply {
                            action = "START"
                            putExtra("TIME_MS", totalMs)
                        }
                        androidx.core.content.ContextCompat.startForegroundService(context, intent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryPurple
                )
            ) {
                Text(
                    text = "Set Limit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        } else {
            // Stop Button Only
            Button(
                onClick = {
                    val intent = android.content.Intent(context, ScreenTimeService::class.java).apply {
                        action = "STOP"
                    }
                    context.startService(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935) // Red color for stop
                )
            ) {
                Text(
                    text = "Stop",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun TimePickerBox(
    label: String,
    values: List<String>,
    initialIndex: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color,
    lightGray: Color,
    borderColor: Color
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    // Pad the list with 2 empty items at top and bottom so the real items can be snapped to the center
    val paddedValues = remember(values) { listOf("", "") + values + listOf("", "") }
    
    val selectedIndex by remember { 
        derivedStateOf { 
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                initialIndex
            } else {
                val viewportCenter = layoutInfo.viewportSize.height / 2
                val centerItem = visibleItemsInfo.minByOrNull { 
                    kotlin.math.abs(it.offset + (it.size / 2) - viewportCenter) 
                }
                val index = (centerItem?.index ?: (initialIndex + 2)) - 2
                index.coerceIn(0, values.size - 1)
            }
        } 
    }

    androidx.compose.runtime.LaunchedEffect(selectedIndex) {
        if (values.isNotEmpty() && selectedIndex in values.indices) {
            onValueChange(values[selectedIndex])
        }
    }

    Column(modifier = modifier) {
        // Top Label (Hours / Minutes)
        Text(
            text = label,
            fontSize = 14.sp,
            color = lightGray,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Bounding Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(paddedValues.size) { index ->
                    val isCenter = index == selectedIndex + 2
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = paddedValues[index],
                            fontSize = if (isCenter) 36.sp else 20.sp,
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCenter) textColor else lightGray
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_7_pro")
@Composable
fun ScreenTimeScreenPreview() {
    ScreentimeTheme {
        ScreenTimeScreen()
    }
}
