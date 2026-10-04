package com.karan.anuj.quality

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * The rules from `.agents/rules/features.md` and `code-integrity.md` that
 * can be checked by reading the code. Each test names the rule it guards.
 */
class ArchitectureTest {

    private val production = Konsist.scopeFromProduction()

    @Test
    fun `the rules in core domain never use Android`() {
        production.files
            .filter { it.path.contains("/core/domain/") }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("android.") || it.name.startsWith("androidx.") }
            }
    }

    @Test
    fun `a feature never depends on another feature`() {
        production.files
            .filter { it.path.contains("/feature/") }
            .assertFalse { file ->
                val own = file.path.substringAfter("/feature/").substringBefore("/")
                file.imports.any { import ->
                    import.name.startsWith(FEATURE_PACKAGE) &&
                        import.name.removePrefix(FEATURE_PACKAGE).substringBefore(".") != own
                }
            }
    }

    @Test
    fun `a feature never reaches into storage`() {
        production.files
            .filter { it.path.contains("/feature/") }
            .assertFalse { file -> file.imports.any { it.name.startsWith("com.karan.anuj.core.data") } }
    }

    @Test
    fun `a view model is given use cases, never a repository`() {
        production.classes()
            .withNameEndingWith("ViewModel")
            .assertFalse { viewModel ->
                viewModel.primaryConstructor?.parameters.orEmpty().any { it.type.name.endsWith("Repository") }
            }
    }

    @Test
    fun `a use case lives with the rules, not with a screen`() {
        production.classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.resideInPackage("com.karan.anuj.core.domain..") }
    }

    private companion object {
        const val FEATURE_PACKAGE = "com.karan.anuj.feature."
    }
}
