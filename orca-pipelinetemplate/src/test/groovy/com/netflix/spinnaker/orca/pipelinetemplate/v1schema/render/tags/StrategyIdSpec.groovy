package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.tags

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class StrategyIdSpec extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()
  Front50Service front50Service = Mock(Front50Service)
  Renderer renderer = new JinjaRenderer(objectMapper, front50Service, [])

  @Subject
  StrategyIdTag subject = new StrategyIdTag(front50Service)

  @Unroll
  def 'should render strategy id'() {
    given:
    front50Service.getStrategies('myApp') >>  [
      [
        name: 'Deploy and destroy server group',
        id: '9595429f-afa0-4c34-852b-01a9a01967f9',
        stages: []
      ],
      [
        name: 'Important strategy',
        id: '1685429e-beb1-4d35-963c-02b9a01977e1',
        stages: []
      ],
      [
        name: "Rob's great strategy",
        id: '1685429e-beb1-4d35-963c-123456789012',
        stages: []
      ]
    ]

    expect:
    renderer.render(
      tag,
      new DefaultRenderContext('myApp',null, [:])
    ) == expectedId

    where:
    tag                                                                         || expectedId
    '{% strategyId application=myApp name="Deploy and destroy server group" %}' || '9595429f-afa0-4c34-852b-01a9a01967f9'
    "{% strategyId name='Important strategy' %}"                                || '1685429e-beb1-4d35-963c-02b9a01977e1'
    '{% strategyId name="Rob\'s great strategy" %}'                             || '1685429e-beb1-4d35-963c-123456789012'
  }

  def 'should render strategy id from another app'() {
    given:
    front50Service.getStrategies('testApp') >>  [
      [
        name: 'Strategy in different app',
        id: '1685429e-beb1-4d35-963c-02b9a01977e1',
        stages: []
      ]
    ]

    expect:
    renderer.render(
      tag,
      new DefaultRenderContext('myApp',null, [:])
    ) == expectedId

    where:
    tag                                                                     || expectedId
    '{% strategyId application=testApp name="Strategy in different app" %}' || '1685429e-beb1-4d35-963c-02b9a01977e1'
  }

  def 'should render strategy id using variables defined in context'() {
    given:
    front50Service.getStrategies('myApp') >>  [
      [
        name: 'Deploy and destroy server group',
        id: '9595429f-afa0-4c34-852b-01a9a01967f9',
        stages: []
      ]
    ]

    RenderContext context = new DefaultRenderContext('myApp', null, [:])
    context.variables.put("pipelineName", "Deploy and destroy server group")
    context.variables.put("applicationName", "myApp")

    expect:
    renderer.render('{% strategyId application=applicationName name=pipelineName %}', context) ==  '9595429f-afa0-4c34-852b-01a9a01967f9'
  }

  def 'should handle missing input params'() {
    given: 'a strategyId tag with no app defined'
    def applicationInContext = 'myApp'
    def context = new DefaultRenderContext(applicationInContext, null, [:])

    when:
    renderer.render('{% strategyId name="Deploy and destroy server group" %}', context)

    then: 'application should be inferred from context'
    1 * front50Service.getStrategies(applicationInContext) >>  [
      [
        name: 'Deploy and destroy server group',
        id: '9595429f-afa0-4c34-852b-01a9a01967f9',
        stages: []
      ]
    ]

    when: 'template is missing required fields (name)'
    renderer.render('{% strategyId application=myApp %}', context)

    then:
    thrown(TemplateRenderException)

    when: 'template is missing required fields (all)'
    renderer.render('{% strategyId %}', context)

    then:
    thrown(TemplateRenderException)

    when: 'no pipeline was not found from provided input'
    renderer.render('{% strategyId name="Deploy and destroy server group" %}', context)

    then:
    1 * front50Service.getStrategies(applicationInContext) >>  []
    thrown(TemplateRenderException)
  }
}
