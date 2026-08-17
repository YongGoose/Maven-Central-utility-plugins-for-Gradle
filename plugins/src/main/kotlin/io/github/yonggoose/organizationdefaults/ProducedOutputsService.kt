package io.github.yonggoose.organizationdefaults

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Records which tasks actually produced their output in this build.
 *
 * [CheckProjectArtifactTask] has to tell a signature, a POM or a `module.json` that this build
 * stands behind from one left in `build/` by an earlier run. The obvious way to ask —
 * `task.state.didWork || task.state.upToDate` — is exactly what the configuration cache forbids,
 * and the obvious replacement, deciding from the task graph at configuration time, cannot see
 * `onlyIf` at all: neither a predicate the build added to a `Sign` task nor the one Gradle itself
 * puts on `GenerateModuleMetadata` for publications with no component. Both of those leave a task
 * enabled and un-excluded while it produces nothing, so a stale file would have been counted.
 *
 * A build event listener sees the outcome itself, and a `BuildService` is the one place a task may
 * hold that across the configuration cache.
 *
 * The mapping to what the old test meant is exact. [TaskSuccessResult] covers both executed and
 * UP-TO-DATE (and FROM-CACHE), which is `didWork || upToDate`. A task skipped by `onlyIf`, by
 * `enabled = false`, or for having no source reports `TaskSkippedResult` instead. A task excluded
 * with `-x` — in any of the spellings Gradle accepts, including abbreviations and project-relative
 * paths — never runs and so reports nothing at all.
 *
 * `checkProjectArtifact` depends on every task it asks about, so their events precede its own
 * execution. Should one ever fail to arrive in time the task under-counts, and an under-count is
 * reported as a missing signature rather than a verified one — the direction this whole check
 * exists to fail in.
 */
abstract class ProducedOutputsService :
    BuildService<BuildServiceParameters.None>,
    OperationCompletionListener {

    private val succeeded: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) {
            return
        }
        if (event.result is TaskSuccessResult) {
            succeeded.add(event.descriptor.taskPath)
        }
    }

    /** Whether the task at [taskPath] produced its output in this build. */
    fun produced(taskPath: String): Boolean = succeeded.contains(taskPath)

    companion object {
        const val NAME: String = "io.github.yonggoose.organizationdefaults.producedOutputs"
    }
}
