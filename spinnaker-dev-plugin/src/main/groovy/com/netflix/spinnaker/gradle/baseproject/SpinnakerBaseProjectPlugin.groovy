package com.netflix.spinnaker.gradle.baseproject

import com.netflix.spinnaker.gradle.dependency.SpinnakerDependencyPlugin
import com.netflix.spinnaker.gradle.idea.SpinnakerIdeaConfigPlugin
import com.netflix.spinnaker.gradle.license.SpinnakerLicenseReportPlugin
import org.owasp.dependencycheck.gradle.DependencyCheckPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class SpinnakerBaseProjectPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(SpinnakerIdeaConfigPlugin)
        project.plugins.apply(SpinnakerDependencyPlugin)
        project.plugins.apply(SpinnakerLicenseReportPlugin)
        project.plugins.apply(DependencyCheckPlugin)

        // This should come last to ensure the bump to Java 1.8 is applied
        // (some Netflix OSS plugins hardcode/revert it to 1.7)
        project.plugins.apply(SpinnakerBaseProjectConventionsPlugin)
    }
}
