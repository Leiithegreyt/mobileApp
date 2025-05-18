package com.example.drivebroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData

class NavigationViewModel : ViewModel() {
    val pendingTripId = MutableLiveData<Int?>(null)
} 