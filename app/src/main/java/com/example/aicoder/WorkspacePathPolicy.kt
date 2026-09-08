package com.example.aicoder

import java.io.File

/** Single source of truth for workspace path validation. */
object WorkspacePathPolicy {
    fun resolve(root: File, path: String, allowRoot: Boolean = false): File {
        val clean = path.trim().replace('\\', '/')
        val base = root.canonicalFile
        if (clean.isBlank()) {
            require(allowRoot) { "Path cannot be empty" }
            return base
        }
        require(!clean.startsWith("/")) { "Absolute paths are not allowed" }
        require(!clean.matches(Regex("^[A-Za-z]:.*"))) { "Drive-letter paths are not allowed" }
        val parts = clean.split('/')
        require(parts.none { it.isBlank() || it == "." }) { "Invalid path" }
        require(parts.none { it == ".." }) { "Parent traversal is not allowed" }
        val result = File(base, clean).canonicalFile
        require(result.toPath().startsWith(base.toPath())) { "Path escapes workspace" }
        return result
    }
}
