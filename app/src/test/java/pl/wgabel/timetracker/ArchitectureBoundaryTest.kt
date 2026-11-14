package pl.wgabel.timetracker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fast source dependency checks: they do not start Android or an emulator. */
class ArchitectureBoundaryTest {
    private val sources = listOf(File("src/main/java"), File("app/src/main/java"))
        .first { it.isDirectory }.walkTopDown().filter { it.extension == "kt" }.toList()

    private fun withoutComments(source: String): String =
        source.replace(Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*"), "")

    @Test fun onlyDataLayerCanAccessSqlite() {
        val violations = sources.filter { file ->
            val source = withoutComments(file.readText())
            !source.startsWith("package pl.wgabel.timetracker.data\n") &&
                ("android.database" in source || Regex("\\.(rawQuery|execSQL|beginTransaction)\\s*\\(").containsMatchIn(source))
        }
        assertTrue("SQLite outside data layer: $violations", violations.isEmpty())
    }

    @Test fun modelsAndDomainDoNotDependOnAndroidViewsOrFeatures() {
        val violations = sources.filter { file ->
            val source = withoutComments(file.readText())
            val isDomain = source.startsWith("package pl.wgabel.timetracker.domain\n") ||
                source.startsWith("package pl.wgabel.timetracker.model\n")
            isDomain && Regex("import (android\\.|pl\\.wgabel\\.timetracker\\.(ui|features)\\.)").containsMatchIn(source)
        }
        assertTrue("Presentation dependency in domain/models: $violations", violations.isEmpty())
    }

    @Test fun featureAndSharedPresentationDoNotDependOnActivity() {
        val violations = sources.filter { file ->
            val source = withoutComments(file.readText())
            (source.startsWith("package pl.wgabel.timetracker.features.") ||
                source.startsWith("package pl.wgabel.timetracker.ui")) &&
                "import pl.wgabel.timetracker.MainActivity" in source
        }
        assertTrue("Activity dependency in presentation: $violations", violations.isEmpty())
    }

    @Test fun packagesBelowCompositionRootHaveNoDependencyCycles() {
        val prefix = "pl.wgabel.timetracker"
        val edges = mutableMapOf<String, MutableSet<String>>()
        sources.forEach { file ->
            val source = withoutComments(file.readText())
            val current = Regex("package ([\\w.]+)").find(source)!!.groupValues[1]
            if (current == prefix) return@forEach
            val dependencies = Regex("import ($prefix[\\w.]+)").findAll(source).map { match ->
                match.groupValues[1].split('.').takeWhile { it.first().isLowerCase() }.joinToString(".")
            }.filter { it != prefix && it != current }
            edges.getOrPut(current) { mutableSetOf() }.addAll(dependencies)
        }
        fun visit(node: String, path: Set<String>) {
            assertTrue("Package cycle: $path -> $node", node !in path)
            edges[node].orEmpty().forEach { visit(it, path + node) }
        }
        edges.keys.forEach { visit(it, emptySet()) }
    }
}
