package com.shohjahon.treble.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shohjahon.treble.data.repository.AuthRepository
import com.shohjahon.treble.data.repository.GameRepository
import com.shohjahon.treble.data.repository.NotificationRepository
import java.util.concurrent.TimeUnit

/**
 * Ilova yopiq bo'lsa ham har ~15 daqiqada serverdan yangiliklarni oladi:
 * yangi biriktirilgan o'yinlar haqida xabar beradi va eslatmalarni yangilaydi.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        AuthRepository.init(app)
        GameRepository.init(app)
        NotificationRepository.init(app)
        ReminderScheduler.init(app)

        if (AuthRepository.currentUser == null) return Result.success()
        GameRepository.refresh()                       // eslatmalar ham qayta o'rnatiladi
        NotificationRepository.sync(app, showSystem = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "treble_background_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
