import systems.danger.kotlin.*
import systems.danger.kotlin.models.github.GitHubUserType

danger(args) {
    val changed = git.modifiedFiles + git.createdFiles
    val sourceChanged = changed.any { "/src/" in it && "/src/test" !in it && "Test/" !in it }
    val changelogChanged = "CHANGELOG.md" in changed

    onGitHub {
        if (pullRequest.user.type == GitHubUserType.BOT) {
            message("Automation PR; skipping checks.")
            return@onGitHub
        }
        val trivial = "#trivial" in pullRequest.title

        // Keep a Changelog: behaviour changes get an [Unreleased] entry (CLAUDE.md).
        if (!trivial && sourceChanged && !changelogChanged) {
            warn(
                "Source changed without a CHANGELOG.md entry. Add one under [Unreleased], or put #trivial in the title."
            )
        }

        // Every .sq change ships a .sqm migration (decision 0007).
        val schemaChanged = changed.any { it.endsWith(".sq") && it in git.modifiedFiles }
        val migrationAdded = git.createdFiles.any { it.endsWith(".sqm") }
        if (schemaChanged && !migrationAdded) {
            fail("A .sq file changed without a new .sqm migration.")
        }

        // Diagnostics stay in every build (decision 0005).
        val diagnosticsRemoved =
            git.deletedFiles.filter {
                "diagnostic" in it.lowercase() || "ridetest" in it.lowercase()
            }
        if (diagnosticsRemoved.isNotEmpty()) {
            warn(
                "Diagnostic code removed: ${diagnosticsRemoved.joinToString()}. Move diagnostics, don't delete them."
            )
        }

        if ((pullRequest.additions ?: 0) + (pullRequest.deletions ?: 0) > 2000) {
            warn("Big PR; consider splitting it.")
        }
    }
}
