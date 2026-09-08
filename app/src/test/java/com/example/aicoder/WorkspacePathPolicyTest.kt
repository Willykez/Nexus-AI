package com.example.aicoder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WorkspacePathPolicyTest {
    private val root = File("build/test-workspace").absoluteFile

    @Test
    fun resolvesSafeRelativePath() {
        val result = WorkspacePathPolicy.resolve(root, "app/src/Main.kt")
        assertEquals(File(root, "app/src/Main.kt").canonicalFile, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsParentTraversal() {
        WorkspacePathPolicy.resolve(root, "../secret.txt")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAbsolutePath() {
        WorkspacePathPolicy.resolve(root, "/secret.txt")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDriveLetterPath() {
        WorkspacePathPolicy.resolve(root, "C:/secret.txt")
    }

    @Test
    fun allowsWorkspaceRootOnlyWhenRequested() {
        assertEquals(root.canonicalFile, WorkspacePathPolicy.resolve(root, "", allowRoot = true))
    }
}
