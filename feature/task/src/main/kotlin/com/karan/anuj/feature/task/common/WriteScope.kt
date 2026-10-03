package com.karan.anuj.feature.task.common

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where changes to tasks are run so that they finish even if the screen that
 * started them closes straight away.
 *
 * A view model's own scope is cancelled the moment its screen is left. A
 * change started just before pressing back (ticking a box, picking a
 * priority, moving a task to the trash) would then be dropped half-way and
 * silently lost. Reads stay in the view model's scope, where stopping with
 * the screen is exactly what is wanted.
 *
 * It starts on the main thread without a hop, so the order in which the
 * user tapped is the order in which the changes begin.
 */
@Singleton
class WriteScope @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }
}
