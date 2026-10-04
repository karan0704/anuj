package com.karan.anuj.core.domain.task

/** One visible row of the task tree. [depth] is 0 for a top-level task. */
data class TaskNode(
    val task: Task,
    val depth: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
)

object TaskTree {

    /** Open tasks first, then by priority, then oldest first so the order does not jump around. */
    val treeOrder: Comparator<Task> =
        compareBy<Task> { !it.isOpen }
            .thenByDescending { it.priority.ordinal }
            .thenBy { it.stamps.createdAt }
            .thenBy { it.id.value }

    /** Timed tasks in clock order, then untimed ones, each group by priority. */
    val dayOrder: Comparator<Task> =
        compareBy<Task> { it.dueTime == null }
            .thenBy { it.dueTime }
            .thenByDescending { it.priority.ordinal }
            .thenBy { it.stamps.createdAt }
            .thenBy { it.id.value }

    /**
     * Turns a flat list of tasks into the rows to draw, top to bottom:
     * each task followed by its children, but only where the parent is in
     * [expanded].
     *
     * A task whose parent is not in [tasks] (filtered out, or finished and
     * hidden) is shown at the top level rather than disappearing.
     */
    fun flatten(tasks: List<Task>, expanded: Set<TaskId>): List<TaskNode> {
        val ids = tasks.mapTo(HashSet()) { it.id }
        val childrenOf = tasks
            .filter { it.parentId != null && it.parentId in ids }
            .groupBy { it.parentId }
        val roots = tasks.filter { it.parentId == null || it.parentId !in ids }

        val rows = ArrayList<TaskNode>(tasks.size)
        val visited = HashSet<TaskId>()

        fun add(task: Task, depth: Int) {
            /** Guards against a parent loop in damaged data; each task is drawn once. */
            if (!visited.add(task.id)) return
            val children = childrenOf[task.id].orEmpty()
            val isExpanded = task.id in expanded
            rows += TaskNode(task, depth, hasChildren = children.isNotEmpty(), expanded = isExpanded)
            if (isExpanded) children.sortedWith(treeOrder).forEach { add(it, depth + 1) }
        }

        roots.sortedWith(treeOrder).forEach { add(it, 0) }
        return rows
    }

    /** The chain of parents above [task], nearest first. */
    fun ancestorsOf(task: Task, byId: Map<TaskId, Task>): List<Task> {
        val chain = mutableListOf<Task>()
        val seen = mutableSetOf(task.id)
        var parent = task.parentId?.let(byId::get)
        while (parent != null && seen.add(parent.id)) {
            chain += parent
            parent = parent.parentId?.let(byId::get)
        }
        return chain
    }
}
