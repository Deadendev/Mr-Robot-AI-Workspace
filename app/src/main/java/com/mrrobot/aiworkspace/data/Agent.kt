package com.mrrobot.aiworkspace.data

import java.util.UUID

data class Agent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String,
    val status: String = "Ready",
    val description: String,
    val systemPrompt: String,
    val skills: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val isActive: Boolean = false,
    val iconEmoji: String = "\uD83E\uDD16", // 🤖
    val createdAt: Long = System.currentTimeMillis()
)

object AgentCatalog {
    val builtInAgents = listOf(
        Agent(
            id = "android_architect",
            name = "Android Architect",
            role = "Mobile Architecture",
            status = "Ready",
            description = "Builds production-grade Android app structures using Kotlin, Compose, MVVM, and clean architecture.",
            systemPrompt = "You are an elite Android architect. Produce production-ready Kotlin and Jetpack Compose solutions.",
            skills = listOf("Kotlin", "Jetpack Compose", "MVVM", "Gradle", "Navigation"),
            isBuiltIn = true,
            iconEmoji = "\uD83C\uDFD7\uFE0F" // 🏗️
        ),
        Agent(
            id = "ui_ux_strategist",
            name = "UI/UX Strategist",
            role = "Premium Interface Design",
            status = "Ready",
            description = "Improves visual hierarchy, cyberpunk styling, accessibility, and app-store-ready UI polish.",
            systemPrompt = "You are a senior UI/UX strategist. Improve mobile UI into premium, polished, market-ready experiences.",
            skills = listOf("Material 3", "Glassmorphism", "Animations", "Design Systems"),
            isBuiltIn = true,
            iconEmoji = "\uD83C\uDFA8" // 🎨
        ),
        Agent(
            id = "backend_engineer",
            name = "Backend Engineer",
            role = "API & Data Systems",
            status = "Ready",
            description = "Designs APIs, repositories, networking layers, data persistence, and AI backend flows.",
            systemPrompt = "You are a backend engineer. Build stable APIs, repositories, networking, and persistence systems.",
            skills = listOf("REST APIs", "OkHttp", "DataStore", "Repositories", "Security"),
            isBuiltIn = true,
            iconEmoji = "\u2699\uFE0F" // ⚙️
        ),
        Agent(
            id = "debug_specialist",
            name = "Debug Specialist",
            role = "Crash & Build Fixing",
            status = "Ready",
            description = "Analyzes Gradle errors, Android crashes, dependency issues, and broken workflows.",
            systemPrompt = "You are a debugging specialist. Find root causes first, then provide exact fixes.",
            skills = listOf("Gradle", "GitHub Actions", "Crash Logs", "Dependency Fixes"),
            isBuiltIn = true,
            iconEmoji = "\uD83D\uDD0D" // 🔍
        ),
        Agent(
            id = "workflow_builder",
            name = "Workflow Builder",
            role = "Automation & CI/CD",
            status = "Ready",
            description = "Creates GitHub Actions, build pipelines, release workflows, and automated upgrade scripts.",
            systemPrompt = "You are a CI/CD automation engineer. Create reliable GitHub Actions and build workflows.",
            skills = listOf("GitHub Actions", "APK Builds", "Artifacts", "Release Pipelines"),
            isBuiltIn = true,
            iconEmoji = "\uD83D\uDE80" // 🚀
        ),
        Agent(
            id = "fullstack_dev",
            name = "Full-Stack Developer",
            role = "End-to-End Development",
            status = "Ready",
            description = "Handles both frontend and backend tasks, from UI components to server logic and database design.",
            systemPrompt = "You are a full-stack developer. Build complete features spanning UI, business logic, networking, and data layers.",
            skills = listOf("Kotlin", "APIs", "Room DB", "Compose", "Testing"),
            isBuiltIn = true,
            iconEmoji = "\uD83D\uDCBB" // 💻
        ),
        Agent(
            id = "security_auditor",
            name = "Security Auditor",
            role = "Security & Privacy",
            status = "Ready",
            description = "Reviews code for vulnerabilities, insecure storage, API key exposure, and privacy compliance.",
            systemPrompt = "You are a security auditor. Identify vulnerabilities, propose mitigations, and ensure secure-by-default patterns.",
            skills = listOf("OWASP", "Encryption", "ProGuard", "API Security", "Privacy"),
            isBuiltIn = true,
            iconEmoji = "\uD83D\uDD12" // 🔒
        ),
        Agent(
            id = "code_reviewer",
            name = "Code Reviewer",
            role = "Quality & Best Practices",
            status = "Ready",
            description = "Reviews pull requests, suggests improvements, enforces coding standards, and catches anti-patterns.",
            systemPrompt = "You are a senior code reviewer. Provide constructive feedback focused on correctness, readability, and performance.",
            skills = listOf("Clean Code", "SOLID", "Performance", "Kotlin Idioms", "Testing"),
            isBuiltIn = true,
            iconEmoji = "\u2705" // ✅
        )
    )

    fun findById(id: String): Agent {
        return builtInAgents.firstOrNull { it.id == id } ?: builtInAgents.first()
    }

    // Keep backward compatibility
    val agents: List<Agent> get() = builtInAgents
}
