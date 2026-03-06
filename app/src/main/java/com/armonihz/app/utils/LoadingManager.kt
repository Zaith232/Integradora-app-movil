package com.armonihz.app.utils


import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.armonihz.app.R

object LoadingManager {

    private var dialog: AlertDialog? = null

    fun show(activity: Activity, message: String = "Cargando...") {

        if (activity.isFinishing) return

        val builder = AlertDialog.Builder(activity)
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_loading, null)

        builder.setView(view)
        builder.setCancelable(false)

        dialog = builder.create()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog?.show()
    }

    fun hide() {
        dialog?.dismiss()
        dialog = null
    }
}