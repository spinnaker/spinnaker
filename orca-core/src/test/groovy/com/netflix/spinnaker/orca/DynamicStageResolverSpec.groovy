/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.plugins.api.internal.ExtensionInvocationHandler
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.WaitStage
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class DynamicStageResolverSpec extends Specification {

  DynamicConfigService dynamicConfigService = Mock()

  StageDefinitionBuildersProvider stageDefinitionBuildersProvider = new StageDefinitionBuildersProvider([
      new WaitStage(),
      new DefaultStageResolverSpec.AliasedStageDefinitionBuilder(),
      new BuilderOne(),
      new BuilderTwo()
  ])

  @Unroll
  def "should lookup stage by name or alias"() {
    when:
    def result = new DynamicStageResolver(dynamicConfigService, stageDefinitionBuildersProvider)
        .getStageDefinitionBuilder(stageTypeIdentifier, null).getType()

    then:
    2 * dynamicConfigService.getConfig(_, _, _) >> BuilderOne.class.canonicalName
    result == expectedStageType

    where:
    stageTypeIdentifier || expectedStageType
    "wait"              || "wait"
    "aliased"           || "aliased"
    "notAliased"        || "aliased"
  }

  @Unroll
  def "should use configured preference on duplicate alias"() {
    when:
    def result = new DynamicStageResolver(dynamicConfigService, stageDefinitionBuildersProvider)
        .getStageDefinitionBuilder("same", null).class

    then:
    result == expectedBuilder
    3 * dynamicConfigService.getConfig(_, _, _) >> preference

    where:
    preference                     || expectedBuilder
    BuilderOne.class.canonicalName || BuilderOne.class
    BuilderTwo.class.canonicalName || BuilderTwo.class
  }

  def "should support extension proxies"() {
    given:
    InvocationHandler handler = new ExtensionInvocationHandler() {
      @Override
      Class<? extends SpinnakerExtensionPoint> getTargetClass() {
        return BuilderOne.class
      }

      @Override
      String getPluginId() {
        return "pluginId";
      }

      @Override
      Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(new BuilderOne(), args)
      }
    }

    List<StageDefinitionBuilder> builders = [
        (StageDefinitionBuilder) Proxy.newProxyInstance(
            this.class.getClassLoader(),
            BuilderOne.class.interfaces,
            handler
        ),
        new BuilderTwo()
    ]

    when:
    def result = new DynamicStageResolver(dynamicConfigService, new StageDefinitionBuildersProvider(builders))
        .getStageDefinitionBuilder("same", null).class

    then:
    result == BuilderOne.class
    3 * dynamicConfigService.getConfig(_, _, _) >> BuilderOne.class.canonicalName
  }

  def "should raise exception when stage not found"() {
    when:
    new DynamicStageResolver(dynamicConfigService, stageDefinitionBuildersProvider)
        .getStageDefinitionBuilder("DoesNotExist", null)

    then:
    thrown(StageResolver.NoSuchStageDefinitionBuilderException)
    4 * dynamicConfigService.getConfig(_, _, _) >> BuilderOne.class.canonicalName
  }

  def "should raise exception when no preference is configured"() {
    when:
    new DynamicStageResolver(
      dynamicConfigService,
      new StageDefinitionBuildersProvider([
          new DefaultStageResolverSpec.AliasedStageDefinitionBuilder(),
          new DefaultStageResolverSpec.AliasedStageDefinitionBuilder()
      ])
    )

    then:
    thrown(DynamicStageResolver.NoPreferenceConfigPresentException)
    1 * dynamicConfigService.getConfig(_, _, _) >> DynamicStageResolver.NO_PREFERENCE
  }

  def "should raise exception when two stage builders have the same canonical name"() {
    when:
    new DynamicStageResolver(
      dynamicConfigService,
      new StageDefinitionBuildersProvider([
          new BuilderOne(),
          new BuilderOne()
      ])
    )

    then:
    thrown(DynamicStageResolver.ConflictingClassNamesException)
    2 * dynamicConfigService.getConfig(_, _, _) >> BuilderOne.class.canonicalName
  }

  @StageDefinitionBuilder.Aliases("same")
  class BuilderOne implements StageDefinitionBuilder {}

  @StageDefinitionBuilder.Aliases("same")
  class BuilderTwo implements StageDefinitionBuilder {}
}
