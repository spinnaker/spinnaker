/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker

import com.jfrog.bintray.gradle.BintrayExtension
import com.netflix.spinnaker.internal.IdeaConfig
import com.netflix.spinnaker.publishing.InternalPublishingTask
import groovy.util.logging.Log4j
import nebula.plugin.bintray.NebulaBintrayPublishingPlugin
import nebula.plugin.bintray.NebulaOJOPublishingPlugin
import nebula.plugin.publishing.maven.NebulaBaseMavenPublishingPlugin
import nebula.plugin.responsible.NebulaResponsiblePlugin
import org.gradle.api.*
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.GradleBuild
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.idea.IdeaPlugin
import release.*

@Log4j
class SpinnakerProjectPlugin implements Plugin<Project> {
  private String defaultCodeStyleXml

  @Override
  void apply(Project project) {
    defaultCodeStyleXml = SpinnakerProjectPlugin.getResourceAsStream("/defaultCodeStyle.xml").text

    project.status = project.version.toString().endsWith('-SNAPSHOT') ? 'integration' : 'release'
    project.plugins.apply(NebulaResponsiblePlugin)
    project.extensions.create("spinnaker", SpinnakerDependencies, project)

    addStandardRepos(project)
    addProvidedScope(project)
    configureBintray(project)
    configureSnapshot(project)
    configureRelease(project)

    project.tasks.create('internalPublish', InternalPublishingTask)
    project.configure(project) {
      def ideaConfig = project.extensions.create("ideaConfig", IdeaConfig)

      afterEvaluate {
        IdeaPlugin ideaPlugin = project == project.rootProject ? project.getPlugins().findPlugin(IdeaPlugin) : project.rootProject.getPlugins().findPlugin(IdeaPlugin)
        if (ideaPlugin && ideaPlugin.model) {
          if (ideaConfig.mainClassName) {
            def springloadedJvmArgs = getSpringloadedJvmArgs(project)
            applyWorkspaceConfig(project.name, project.projectDir.canonicalPath, springloadedJvmArgs,
                ideaConfig.mainClassName, ideaPlugin.model.workspace.iws)
          }
          if (project == project.rootProject) {
            applyProjectConfig(ideaConfig, ideaPlugin.model.project.ipr)
          }
          ideaPlugin.model.project.jdkName = project.sourceCompatibility
          ideaPlugin.model.project.languageLevel = project.sourceCompatibility
        }
      }
    }
  }

  void addStandardRepos(Project project) {
    project.repositories.jcenter()
    project.repositories.maven {
      name 'Bintray spinnaker repo'
      url 'http://dl.bintray.com/spinnaker/spinnaker'
    }
  }

  static void addProvidedScope(Project project) {
    project.plugins.apply(JavaPlugin)
    project.plugins.apply(IdeaPlugin)

    def provided = project.configurations.maybeCreate('provided')
    project.sourceSets.main.compileClasspath += provided
    project.idea.module.scopes.COMPILE.plus += provided
  }

  static void configureBintray(Project project) {
    project.plugins.apply(NebulaBintrayPublishingPlugin)
    project.plugins.apply(NebulaOJOPublishingPlugin)

    BintrayExtension bintray = project.extensions.getByType(BintrayExtension)
    bintray.pkg.userOrg = 'spinnaker'
    bintray.pkg.repo = 'spinnaker'
    bintray.pkg.labels = ['spinnaker', 'groovy', 'Netflix']
    project.tasks.publish.dependsOn('bintrayUpload')

    // correct bintray's assumption that we don't want to publish from a subproject
    project.tasks.bintrayUpload.metaClass.setSubtaskSkipPublish = {
      // ignore
    }
    project.tasks.bintrayUpload.metaClass.getSubtaskSkipPublish = {
      false
    }
  }

  static void configureSnapshot(Project project) {
    project.tasks.matching { it.name == 'artifactoryPublish' }.all {
      project.tasks.create('snapshot').dependsOn(it)
    }
  }

  void configureRelease(Project project) {
    // Release, to be replaced by our own in the future.
    project.rootProject.plugins.apply(ReleasePlugin)
    project.rootProject.ext.'gradle.release.useAutomaticVersion' = "true"

    ReleasePluginConvention releaseConvention = project.rootProject.convention.getPlugin(ReleasePluginConvention)
    releaseConvention.failOnUnversionedFiles = false
    releaseConvention.failOnCommitNeeded = false

    // We routinely release from different branches, which aren't master
    def gitReleaseConvention = (GitReleasePluginConvention) releaseConvention.git
    gitReleaseConvention.requireBranch = null
    gitReleaseConvention.pushToCurrentBranch = true

    try {
      def spawnBintrayUpload = project.rootProject.task('spawnBintrayUpload', description: 'Create GradleBuild to run BintrayUpload to use a new version', group: 'Release', type: GradleBuild) {
        startParameter = project.rootProject.getGradle().startParameter.newInstance()
        tasks = ['bintrayUpload']
      }
      project.rootProject.tasks.createReleaseTag.dependsOn spawnBintrayUpload
    } catch (InvalidUserDataException iude) {
      //task already created, ignore
    }
  }

  static void applyWorkspaceConfig(String projectName, String projectDir, String springLoadedJvmArgs, String appClassName, XmlFileContentMerger iws) {
    iws.withXml { provider ->
      def node = provider.asNode()
      def runManagerConfig = node['component'].find { it.'@name' == 'RunManager' } as Node
      if (!runManagerConfig) {
        runManagerConfig = node.appendNode('component', [name: 'RunManager'])
      }

      runManagerConfig.append(new XmlParser().parseText("""
            <configuration default="false" name="Run ${projectName}" type="Application" factoryName="Application">
              <extension name="coverage" enabled="false" merge="false" />
              <option name="MAIN_CLASS_NAME" value="${appClassName}" />
              <option name="VM_PARAMETERS" value="${springLoadedJvmArgs ? '&quot;' + springLoadedJvmArgs + '&quot; &quot;-noverify&quot;' : ''}" />
              <option name="PROGRAM_PARAMETERS" value="" />
              <option name="WORKING_DIRECTORY" value="${projectDir}" />
              <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="false" />
              <option name="ALTERNATIVE_JRE_PATH" value="" />
              <option name="ENABLE_SWING_INSPECTOR" value="false" />
              <option name="ENV_VARIABLES" />
              <option name="PASS_PARENT_ENVS" value="true" />
              <module name="${projectName}" />
              <envs />
              <RunnerSettings RunnerId="Debug">
                <option name="DEBUG_PORT" value="63810" />
                <option name="TRANSPORT" value="0" />
                <option name="LOCAL" value="true" />
              </RunnerSettings>
              <RunnerSettings RunnerId="Run" />
              <ConfigurationWrapper RunnerId="Debug" />
              <ConfigurationWrapper RunnerId="Run" />
              <method />
            </configuration>
        """))

    }
  }

  void applyProjectConfig(IdeaConfig ideaConfig, XmlFileContentMerger ipr) {
    String codeStyleXmlTxt
    if (ideaConfig.codeStyleXml) {
      codeStyleXmlTxt = ideaConfig.codeStyleXml.text
    } else {
      log.warn "No codestyle XML file found! Applying default codestyle rules..."
      codeStyleXmlTxt = defaultCodeStyleXml
    }
    ipr.withXml { provider ->
      def node = provider.asNode()
      node.component.find { it.'@name' == 'VcsDirectoryMappings' }?.mapping[0].'@vcs' = 'Git'

      if (ideaConfig.codeStyleXml) {
        node.append(new XmlParser().parseText(codeStyleXmlTxt))
      }

      def copyrightManager = node.component.find { it.'@name' == 'CopyrightManager' }
      copyrightManager.@default = "ASL2"
      def aslCopyright = copyrightManager.copyright.find { it.option.find { it.@name == "myName" }?.@value == "ASL2" }
      if (aslCopyright == null) {
        copyrightManager.append(new XmlParser().parseText("""
            <copyright>
              <option name="notice" value="Copyright \$today.year Netflix, Inc.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;   http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License." />
              <option name="keyword" value="Copyright" />
              <option name="allowReplaceKeyword" value="" />
              <option name="myName" value="ASL2" />
              <option name="myLocal" value="true" />
            </copyright>
          """))
      }
    }

  }

  static String getSpringloadedJvmArgs(Project project) {
    def mainSourceSet = project.convention.getPlugin(JavaPluginConvention).sourceSets.main
    def jarFile = JarFinder.find("org.springsource.loaded.SpringLoaded", mainSourceSet.compileClasspath.files)
    if (jarFile) {
      "-javaagent:${jarFile.absolutePath}"
    } else {
      ""
    }
  }

  static class JarFinder {
    static File find(String className, Collection<File> classpath) {
      findJarFile(maybeLoadClass(className, toClassLoader(classpath)))
    }

    private static File findJarFile(Class<?> targetClass) {
      if (targetClass) {
        String absolutePath = targetClass.getResource('/' + targetClass.getName().replace(".", "/") + ".class").path
        String jarPath = absolutePath.substring("file:".length(), absolutePath.lastIndexOf("!"))
        new File(jarPath)
      } else {
        null
      }
    }

    private static ClassLoader toClassLoader(Collection<File> classpath) {
      List<URL> urls = new ArrayList<URL>(classpath.size())
      for (File file in classpath) {
        try {
          urls.add(file.toURI().toURL())
        } catch (MalformedURLException ignore) {
        }
      }
      new URLClassLoader(urls as URL[])
    }

    private static Class<?> maybeLoadClass(String className, ClassLoader classLoader) {
      if (classLoader) {
        try {
          return classLoader.loadClass(className)
        } catch (ClassNotFoundException ignore) {
        }
      }
      null
    }
  }
}
