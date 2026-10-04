package com.karan.anuj.feature.reminder.di

import com.karan.anuj.core.domain.reminder.AlarmGateway
import com.karan.anuj.core.domain.reminder.ReminderNotifier
import com.karan.anuj.feature.reminder.platform.AndroidAlarmGateway
import com.karan.anuj.feature.reminder.platform.AndroidReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Gives the reminder rules their two connections to the phone: the alarm and the notification. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    abstract fun bindAlarmGateway(impl: AndroidAlarmGateway): AlarmGateway

    @Binds
    abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
}
