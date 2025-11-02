package com.example.drivebroom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData

class NavigationViewModel : ViewModel() {
    val pendingTripId = MutableLiveData<Int?>(null)
    val navigateToTripLogs = MutableLiveData<Boolean>(false)
    val cameFromNextSchedule = MutableLiveData<Boolean>(false)
    val showNextScheduleRequested = MutableLiveData<Boolean>(false)
} 