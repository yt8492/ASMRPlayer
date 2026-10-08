package com.yt8492.asmrplayer.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 生成元をDIに限定し、パッケージ分割後の逆依存を検出する。 */
class DependencyBoundaryTest {
    private val sources: File = listOf(File("src/main/java"), File("app/src/main/java"))
        .first { it.isDirectory }

    @Test fun uiUsesContractsAndDoesNotConstructDataImplementations() {
        val forbidden = Regex("import com\\.yt8492\\.asmrplayer\\.data\\.(local|datasource|library|repository\\.impl)\\.")
        val violations = sources.walkTopDown().filter { it.extension == "kt" && "/ui/" in it.path }
            .filter { forbidden.containsMatchIn(it.readText()) }.map { it.path }.toList()
        assertTrue("UIからデータ実装への依存: $violations", violations.isEmpty())
    }
    @Test fun dataAndDomainNeverDependOnUiOrService() {
        val forbidden = Regex("import com\\.yt8492\\.asmrplayer\\.(ui|service|di|navigation)\\.")
        val violations = sources.walkTopDown().filter { it.extension == "kt" && ("/data/" in it.path || "/domain/" in it.path) }
            .filter { forbidden.containsMatchIn(it.readText()) }.map { it.path }.toList()
        assertTrue("下位層から上位層への依存: $violations", violations.isEmpty())
    }
}
