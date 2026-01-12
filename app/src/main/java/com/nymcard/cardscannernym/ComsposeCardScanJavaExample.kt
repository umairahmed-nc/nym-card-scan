package com.nymcard.cardscannernym

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nymcard.cardsscan.activity.ScanActivity

@Composable
fun ComposeCardScanJavaExample() {
    var scannedCard by remember { mutableStateOf<String>("") }
    var showEmbeddedScanner by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf("Default") }
    var selectedConfig by remember { mutableStateOf("Default") }
    val activity = LocalContext.current
    // Activity launcher for card scanning
    val scanCardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val debitCard = ScanActivity.debitCardFromResult(data!!)
            debitCard?.let {
                scannedCard = it.number.toString()
                // parse your card scan result from 'data'
            }
        }
    }


    val intent = ScanActivity.start(
        activity as Activity,
        "Scan Card",
        "Scan the front side of your card"
    )


    Column(modifier = Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Button(
            onClick = {
                scanCardLauncher.launch(
                    intent
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Launch Activity Scanner")
        }

        if (scannedCard.isNotEmpty())
            Text(
                text = "Number: $scannedCard",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
    }

}