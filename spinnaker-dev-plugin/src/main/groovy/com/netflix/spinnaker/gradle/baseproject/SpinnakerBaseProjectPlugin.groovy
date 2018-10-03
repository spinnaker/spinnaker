package com.netflix.spinnaker.gradle.baseproject

import com.netflix.spinnaker.gradle.dependency.SpinnakerDependencyPlugin
import com.netflix.spinnaker.gradle.idea.SpinnakerIdeaConfigPlugin
import com.netflix.spinnaker.gradle.license.SpinnakerLicenseReportPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class SpinnakerBaseProjectPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(SpinnakerBaseProjectConventionsPlugin)
        project.plugins.apply(SpinnakerIdeaConfigPlugin)
        project.plugins.apply(SpinnakerDependencyPlugin)
    }
}
