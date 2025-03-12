package com.netflix.spinnaker.front50.model.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.api.model.Timestamped
import com.netflix.spinnaker.front50.api.model.pipeline.Trigger
import com.netflix.spinnaker.front50.jackson.mixins.PipelineMixins
import com.netflix.spinnaker.front50.jackson.mixins.TimestampedMixins
import spock.lang.Specification

class PipelineSpec extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()

  void setup() {
    objectMapper.addMixIn(Pipeline.class, PipelineMixins.class)
    objectMapper.addMixIn(Timestamped.class, TimestampedMixins.class)
  }

  def 'should set any additional pipeline properties when deserializing JSON to Pipeline'() {
    given:
    String pipelineJSON = '{"foo": "bar"}'

    Pipeline pipeline = objectMapper.readValue(pipelineJSON, Pipeline.class)

    expect:
    pipeline.getAny() == [foo: "bar"]
  }

  def 'roundtrip (JSON -> Pipeline -> JSON) retains arbitrary values'() {
    given:
    String pipelineJSON = '{"id":"1","name":"sky","application":"almond","schema":"1","triggers":[],"lastModifiedBy":"anonymous","foo":"bar"}'


    String pipeline = objectMapper.writeValueAsString(objectMapper.readValue(pipelineJSON, Pipeline.class))

    expect:
    pipeline == pipelineJSON
  }

  def 'setting lastModified on pipeline sets updateTs'() {
    given:
    String pipelineJSON = '{"id":"1","name":"sky","application":"almond","schema":"1","triggers":[],"updateTs":"1"}'
    Pipeline pipelineObj = objectMapper.readValue(pipelineJSON, Pipeline.class)

    pipelineObj.setLastModified(new Long(1))
    String pipeline = objectMapper.writeValueAsString(pipelineObj)

    expect:
    pipeline == pipelineJSON
  }

  def 'should grab triggers after deserializing JSON into Pipeline'() {
    given:
    String pipelineJSON = '{"triggers": [{"type": "cron", "id": "a"}, {"type": "cron", "id": "b"}]}'

    ArrayList<Trigger> triggers = new ArrayList<Trigger>();
    Trigger triggerA = new Trigger();
    triggerA.put("type", "cron");
    triggerA.put("id", "a");
    triggers.add(triggerA);

    Trigger triggerB = new Trigger();
    triggerB.put("type", "cron");
    triggerB.put("id", "b");
    triggers.add(triggerB);

    Pipeline pipeline = objectMapper.readValue(pipelineJSON, Pipeline.class)

    expect:
    pipeline.getTriggers() == triggers
  }
}
