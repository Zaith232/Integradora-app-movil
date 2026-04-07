package com.armonihz.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileSharedViewModel : ViewModel() {

    private val _profilePhotoUrl = MutableLiveData<String?>()
    val profilePhotoUrl: LiveData<String?> = _profilePhotoUrl

    fun updatePhoto(url: String?) {
        _profilePhotoUrl.value = url
    }
}