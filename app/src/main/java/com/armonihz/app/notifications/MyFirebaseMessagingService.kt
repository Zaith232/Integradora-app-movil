package com.armonihz.app.notifications // Cambia esto si lo pusiste en otra carpeta

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.armonihz.app.HomeFragment
import com.armonihz.app.R

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // Se ejecuta cuando el token cambia o se genera por primera vez
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Nuevo token: $token")
        // Aquí podrías enviar el token a tu API, pero lo haremos desde el MainActivity para asegurar que el usuario ya inició sesión.
    }

    // Se ejecuta cuando recibes una notificación y la app está ABIERTA
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        remoteMessage.notification?.let {
            mostrarNotificacion(it.title, it.body)
        }
    }

    private fun mostrarNotificacion(titulo: String?, mensaje: String?) {
        val intent = Intent(this, HomeFragment::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val canalId = "armonihz_notificaciones"
        val builder = NotificationCompat.Builder(this, canalId)
            .setSmallIcon(R.mipmap.ic_launcher) // Cambia esto por el icono de tu app si tienes uno transparente
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // En Android 8.0+ se requieren canales de notificación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(canalId, "Notificaciones de Armonihz", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(canal)
        }

        manager.notify(0, builder.build())
    }
}