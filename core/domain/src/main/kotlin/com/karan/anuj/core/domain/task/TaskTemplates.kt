package com.karan.anuj.core.domain.task

import com.karan.anuj.core.domain.record.RecordStamps
import com.karan.anuj.core.domain.time.TimeSource
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * A ready-made task the user can add with one tap.
 *
 * @property steps become sub-tasks, each ticked off on its own
 */
data class TaskTemplate(
    val key: String,
    val name: String,
    val time: LocalTime? = null,
    val repetition: Repetition? = Repetition.Daily(),
    val steps: List<String> = emptyList(),
)

/**
 * The routines offered on first run. Their text ends up as the user's own
 * task data, which they are free to rename, so it lives here with the rest
 * of the task rules rather than with the screen labels.
 */
object BuiltInTemplates {

    val all: List<TaskTemplate> = listOf(
        TaskTemplate(
            key = "morning",
            name = "Morning routine",
            time = LocalTime.of(7, 0),
            steps = listOf("Drink a glass of water", "Take medication", "Brush teeth", "Get dressed", "Eat breakfast"),
        ),
        TaskTemplate(
            key = "leaving_home",
            name = "Leaving home",
            steps = listOf("Keys in pocket", "Phone and wallet", "Lights and fans off", "Taps closed", "Door locked"),
        ),
        TaskTemplate(
            key = "water",
            name = "Drink water",
            steps = listOf("Morning", "Midday", "Afternoon", "Evening"),
        ),
        TaskTemplate(
            key = "sleep",
            name = "Go to bed on time",
            time = LocalTime.of(23, 0),
            steps = listOf("Phone on charger", "Alarm set", "Lights off"),
        ),
    )

    val starterTags: List<String> = listOf("Home", "Work", "Health", "Errands")
}

class ApplyTemplatesUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    /** Creates one task per template, starting [today]. */
    suspend operator fun invoke(templates: List<TaskTemplate>, today: LocalDate) {
        if (templates.isEmpty()) return
        val now = time.nowMillis()
        val newTasks = mutableListOf<Task>()

        templates.forEach { template ->
            val parent = Task(
                id = TaskId(ids.newId()),
                name = template.name,
                dueDate = today,
                dueTime = template.time,
                repetition = template.repetition,
                stamps = RecordStamps.created(now),
            )
            newTasks += parent
            /** Created times one millisecond apart keep the steps in the order they were written. */
            template.steps.forEachIndexed { index, step ->
                newTasks += Task(
                    id = TaskId(ids.newId()),
                    parentId = parent.id,
                    name = step,
                    stamps = RecordStamps.created(now + index),
                )
            }
        }
        tasks.save(newTasks)
    }
}
