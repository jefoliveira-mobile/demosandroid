/*
 * Copyright 2023 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.konan.file.File

class DocumentationPlugin : Plugin<Project> {

    private val taskName = "DocsFileTask"
    private val outputFilePath = "../reports"

    override fun apply(target: Project) {
        with(target) {
            println("Project root dir: ${this.rootDir}")
            val appModuleDocs = AppModuleDocs(this)
            appModuleDocs.generateDocsWithSettingFile()
        }
    }
}

data class UmlModel(val label: String, val absolutePath: String, val itemNode: String)

private const val START_WBS_DIAGRAM = "@startwbs \n \n!theme blueprint"

private const val DIAGRAM_TOPIC = "*"
private const val DIAGRAM_SUBTOPIC = "**"

private const val SETTINGS_GRADLE_KTS = "settings.gradle.kts"
private const val SETTINGS_GRADLE = "settings.gradle"

private const val END_WBS_DIAGRAM = "@endwbs"

private const val INCLUDE_SETTINGS_START = "include(\""

private const val INCLUDE_SETTINGS_END = "\")"

internal class AppModuleDocs(val project: Project) {

    private val listOfUmlModel: MutableList<UmlModel> = mutableListOf()

    fun generateDocsWithSettingFile() {
        with(project) {
            val settingsFile =
                getSettingsOrNull(SETTINGS_GRADLE_KTS) ?: getSettingsOrNull(SETTINGS_GRADLE)
            val builder = StringBuilder()
            buildDiagramModel(settingsFile, builder)
            createOutputUmlFile(builder)
            println("Task Wbs settings.gradle included")
        }
    }

    private fun Project.createOutputUmlFile(builder: StringBuilder) {
        val file = project.file("$buildDir/reports-uml/settings.puml")
        val fileDir = project.file("$buildDir/reports-uml")
        createFileWithTask(project,
            builder.toString(),
            fileDir.absolutePath,
            file.absolutePath
        )
    }

    private fun Project.buildDiagramModel(
        settingsFile: File?,
        builder: StringBuilder
    ) {
        settingsFile?.let { file ->
            val hashOfKeys = hashMapOf<String, List<String>>().toMutableMap()

            extractSettingsFileToMap(file, hashOfKeys)

            generateDiagramOutputString(builder, hashOfKeys)
        }
    }

    private fun extractSettingsFileToMap(
        file: File,
        hashOfKeys: MutableMap<String, List<String>>
    ) {
        file.readStrings().mapNotNull { line ->
            if (line.startsWith(INCLUDE_SETTINGS_START)) {
                val split = line.replace(INCLUDE_SETTINGS_START, "").replace(INCLUDE_SETTINGS_END, "")
                    .split(":")
                val map =
                    split.mapNotNull { items -> if (items.isNotEmpty() && items.isNotBlank()) items else null }
                map
            } else null
        }.forEach { stringList ->
            val key = stringList.first()
            val listWithoutKey = stringList.filter { it != key }
            if (hashOfKeys.containsKey(key)) {
                hashOfKeys[key] = hashOfKeys[key]?.plus(listWithoutKey) ?: listWithoutKey
            } else {
                hashOfKeys[key] = listWithoutKey
            }
        }
    }

    private fun Project.generateDiagramOutputString(
        builder: StringBuilder,
        hashOfKeys: MutableMap<String, List<String>>
    ): StringBuilder {
        builder.appendLine(START_WBS_DIAGRAM)
        builder.appendLine("$DIAGRAM_TOPIC ${project.rootProject.name}")
        hashOfKeys.forEach {
            val marker = DIAGRAM_SUBTOPIC
            builder.appendLine("$marker ${it.key}")
            it.value.forEach { value -> builder.appendLine("$marker$DIAGRAM_TOPIC $value") }
        }
        return builder.appendLine(END_WBS_DIAGRAM)
    }

    private fun Project.getSettingsOrNull(settingsName: String): File? {
        val settingsKts = "${this.rootDir.absolutePath}${File.separator}$settingsName"
        val settingsKtsFile = File(settingsKts)
        return if (settingsKtsFile.exists) settingsKtsFile else null
    }

    private fun createFileWithTask(
        project: Project,
        value: String,
        fileDir: String,
        file: String,
        taskName: String = "SettingsGradleWbs",
    ) {
        project.tasks.register(
            taskName,
            DocsFileTask::class.java,
            value,
            project.file(fileDir),
            project.file(file)
        )
    }
}