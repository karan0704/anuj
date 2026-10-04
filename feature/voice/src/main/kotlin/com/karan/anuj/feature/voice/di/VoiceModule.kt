package com.karan.anuj.feature.voice.di

import com.karan.anuj.core.domain.voice.AddTaskCommand
import com.karan.anuj.core.domain.voice.ChecklistCommand
import com.karan.anuj.core.domain.voice.ListTasksCommand
import com.karan.anuj.core.domain.voice.MarkDoneCommand
import com.karan.anuj.core.domain.voice.PriorityTasksCommand
import com.karan.anuj.core.domain.voice.SearchCommand
import com.karan.anuj.core.domain.voice.Speaker
import com.karan.anuj.core.domain.voice.SpeechEngine
import com.karan.anuj.core.domain.voice.TimerCommand
import com.karan.anuj.core.domain.voice.TopicTasksCommand
import com.karan.anuj.core.domain.voice.Torch
import com.karan.anuj.core.domain.voice.TorchCommand
import com.karan.anuj.core.domain.voice.UndoCommand
import com.karan.anuj.core.domain.voice.VoiceCommand
import com.karan.anuj.core.domain.voice.WhatNowCommand
import com.karan.anuj.feature.voice.platform.AndroidSpeaker
import com.karan.anuj.feature.voice.platform.AndroidTorch
import com.karan.anuj.feature.voice.platform.VoskSpeechEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Gives the assistant its connections to the phone, and the list of things
 * it can be asked. A new command is one more `@IntoSet` line here and a new
 * class in core:domain; the assistant itself is not edited.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    abstract fun bindSpeechEngine(impl: VoskSpeechEngine): SpeechEngine

    @Binds
    abstract fun bindSpeaker(impl: AndroidSpeaker): Speaker

    @Binds
    abstract fun bindTorch(impl: AndroidTorch): Torch

    @Binds @IntoSet
    abstract fun undo(command: UndoCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun torch(command: TorchCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun timer(command: TimerCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun addTask(command: AddTaskCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun search(command: SearchCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun markDone(command: MarkDoneCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun checklist(command: ChecklistCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun whatNow(command: WhatNowCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun priorityTasks(command: PriorityTasksCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun topicTasks(command: TopicTasksCommand): VoiceCommand

    @Binds @IntoSet
    abstract fun listTasks(command: ListTasksCommand): VoiceCommand
}
