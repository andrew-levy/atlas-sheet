package dev.atlas.sheet.findmy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class FindMyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FindMyApp() }
    }
}
