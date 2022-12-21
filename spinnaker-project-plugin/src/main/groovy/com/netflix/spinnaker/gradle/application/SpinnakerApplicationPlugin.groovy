package com.netflix.spinnaker.gradle.application

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.jvm.tasks.Jar

class SpinnakerApplicationPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(ApplicationPlugin)
        String appName = project.rootProject.name
        def appConvention = project.convention.getPlugin(ApplicationPluginConvention)
        appConvention.applicationName = appName
        appConvention.applicationDistribution.from(project.file("config/${appName}.yml")) {
            into('config')
        }
        appConvention.applicationDefaultJvmArgs << "-Djava.security.egd=file:/dev/./urandom"

        project.tasks.withType(CreateStartScripts) {
            it.defaultJvmOpts = appConvention.applicationDefaultJvmArgs + ["-Dspring.config.import=optional:/opt/spinnaker/config/"]
            it.doLast {
                unixScript.text = unixScript.text.replace('DEFAULT_JVM_OPTS=', '''\
                    if [ -f /etc/default/spinnaker ]; then
                      set -a
                      . /etc/default/spinnaker
                      set +a
                    fi
                    DEFAULT_JVM_OPTS='''.stripIndent())
                unixScript.text = unixScript.text.replace('CLASSPATH=$APP_HOME', 'CLASSPATH=$APP_HOME/config:$APP_HOME')
                windowsScript.text = windowsScript.text.replace('set CLASSPATH=', 'set CLASSPATH=%APP_HOME%\\config;')
            }
        }
        project.plugins.withType(JavaBasePlugin) {
            def java = project.convention.getPlugin(JavaPluginConvention)
            def mainSrc = java.sourceSets.getByName('main')
            mainSrc.resources.srcDir('src/main/resources')
            mainSrc.resources.srcDir('config')
        }

        project.tasks.withType(Jar) {
            it.exclude("${appName}.yml")
        }
    }
}
