package com.armonihz.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.armonihz.app.MainActivity // CAMBIO: Importamos MainActivity
import com.armonihz.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Nuevo token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Extraer la información oculta (data payload) que manda Laravel
        val hiringRequestId = remoteMessage.data["hiring_request_id"]
        val status = remoteMessage.data["status"]

        remoteMessage.notification?.let {
            mostrarNotificacion(it.title, it.body, hiringRequestId, status)
        }
    }

    private fun mostrarNotificacion(titulo: String?, mensaje: String?, hiringRequestId: String?, status: String?) {
        // CAMBIO: El Intent debe apuntar a MainActivity, no a un Fragment
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // Adjuntamos los datos al Intent para que MainActivity sepa qué abrir
        if (hiringRequestId != null) {
            intent.putExtra("hiring_request_id", hiringRequestId)
            intent.putExtra("status", status)
        }

        // CAMBIO: Usar FLAG_UPDATE_CURRENT para que no se borren los datos extra (extras)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canalId = "armonihz_notificaciones"
        val builder = NotificationCompat.Builder(this, canalId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(canalId, "Notificaciones de Armonihz", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(canal)
        }

        manager.notify(System.currentTimeMillis().toInt(), builder.build()) // Usar ID único para no sobreescribir notificaciones
    }
}