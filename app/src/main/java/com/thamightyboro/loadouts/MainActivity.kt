package com.thamightyboro.loadouts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thamightyboro.loadouts.ui.App
import com.thamightyboro.loadouts.ui.LoadoutsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoadoutsTheme {
                val vm: AppViewModel = viewModel()
                App(vm)
            }
        }
    }
}
