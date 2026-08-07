package com.walkieyappie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.walkieyappie.app.ui.WalkieTalkieScreen
import com.walkieyappie.app.ui.WalkieTalkieViewModel
import com.walkieyappie.app.ui.theme.WalkieYappieTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WalkieTalkieViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WalkieYappieTheme {
                WalkieTalkieScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startMeshNetwork()
    }
}
