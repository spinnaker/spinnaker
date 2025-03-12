package com.netflix.spinnaker.gradle.extension

import org.gradle.api.Project

fun getParent(project: Project): Project = project.parent ?: project.rootProject
