package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import groovy.text.SimpleTemplateEngine
import io.kubernetes.client.models.V1beta2StatefulSet
import spock.lang.Specification


class KubernetesStatefulSetHandlerSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def handler = new KubernetesStatefulSetHandler()
  def gsonObj = new Gson()

  def IMAGE = "gcr.io/project/image"
  def ACCOUNT = "my-account"
  def CLUSTER_SIZE = 5
  def VERSION = "version"
  def NAMESPACE = "my-namespace"
  def NAME = "my-name"
  def KIND = KubernetesKind.STATEFUL_SET

  String statefulSetManifestWithPartition(Integer partition = CLUSTER_SIZE) {
    def sourceJson = KubernetesStatefulSetHandler.class.getResource("statefulsetpartitionbase.json").getText("utf-8")
    def templateEngine = new SimpleTemplateEngine()
    def binding = [
      "partition": partition,
      "name": getNAME(),
      "namespace": getNAMESPACE(),
      "replicas": getCLUSTER_SIZE(),
      "image": getIMAGE(),
    ]
    def template = templateEngine.createTemplate(sourceJson).make(binding)
    return template.toString()
  }

  String statefulSetManifest() {
    def sourceJson = KubernetesStatefulSetHandler.class.getResource("statefulsetbase.json").getText("utf-8")
    def templateEngine = new SimpleTemplateEngine()
    def binding = [
      "name": getNAME(),
      "namespace": getNAMESPACE(),
      "replicas": getCLUSTER_SIZE(),
      "image": getIMAGE(),
    ]
    def template = templateEngine.createTemplate(sourceJson).make(binding)
    return template.toString()
  }

  V1beta2StatefulSet stringToManifest(Object input) {
    def manifest = KubernetesCacheDataConverter.convertToManifest(input)
    return KubernetesCacheDataConverter.getResource(manifest, V1beta2StatefulSet.class);
  }

  def "[status] wait for stable state to be observed status is null"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 0,
    "currentReplicas": 0,
    "readyReplicas": 0,
    "replicas": 0,
    "updatedReplicas": 0,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifest(), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifest = KubernetesCacheDataConverter.convertToManifest(baseJson << statusJson)
    def status = handler.status(manifest)

    then:
    status.stable == null
  }

  def "[status] rollout status observable"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 1,
    "currentReplicas": 0,
    "readyReplicas": 0,
    "replicas": 0,
    "updatedReplicas": 0,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifest(), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifest = KubernetesCacheDataConverter.convertToManifest(baseJson << statusJson)
    def status = handler.status(manifest)

    then:
    !status.stable.state
    status.stable.message == "Waiting for at least the desired replica count to be met"
  }

  def "[status] wait for stable state to be observed"() {
    when:
    def baseJson = gsonObj.fromJson(statefulSetManifest(), Object)
    def manifest = stringToManifest(baseJson)
    def status = handler.status(manifest)

    then:
    !status.stable.state
    status.stable.message == "No status reported yet"
  }

  def "[status] wait for at least the desired replica count to be met"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 1,
    "currentReplicas": 0,
    "readyReplicas": 0,
    "replicas": 0,
    "updatedReplicas": 0,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifest(), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifestJson = baseJson << statusJson
    def manifest = stringToManifest(manifestJson)
    def status = handler.status(manifest)

    then:
    !status.stable.state
    status.stable.message == "Waiting for at least the desired replica count to be met"
  }

  def "[status] wait for the updated revision to match the current revision"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 1,
    "currentReplicas": 5,
    "readyReplicas": 5,
    "replicas": 5,
    "updatedReplicas": 5,
    "currentRevision": "$NAME-new-my-version",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifest(), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifestJson = baseJson << statusJson
    def manifest = stringToManifest(manifestJson)
    def status = handler.status(manifest)


    then:
    !status.stable.state
    status.stable.message == "Waiting for the updated revision to match the current revision"
  }

  def "[status] wait for all updated replicas to be scheduled"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 1,
    "currentReplicas": 4,
    "readyReplicas": 5,
    "replicas": 5,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifest(), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifestJson = baseJson << statusJson
    def manifest = stringToManifest(manifestJson)
    def status = handler.status(manifest)

    then:
    !status.stable.state
    status.stable.message == "Waiting for all updated replicas to be scheduled"
  }

  def "[status] rollout complete"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 2,
    "currentReplicas": 5,
    "readyReplicas": 5,
    "replicas": 5,
    "updatedReplicas": 5,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifest(), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifestJson = baseJson << statusJson
    def manifest = stringToManifest(manifestJson)
    def status = handler.status(manifest)

    then:
    status.stable.state
    status.stable.message == null
  }

  def "[status] wait for all updated replicas to be ready"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 1,
    "currentReplicas": 5,
    "readyReplicas": 4,
    "replicas": 5,
    "updatedReplicas": 0,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifestWithPartition(), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifestJson = baseJson << statusJson
    def manifest = stringToManifest(manifestJson)
    def status = handler.status(manifest)

    then:
    !status.stable.state
    status.stable.message == "Waiting for all updated replicas to be ready"
  }

  def "[status] wait for partitioned roll out to finish"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 1,
    "currentReplicas": 5,
    "readyReplicas": 5,
    "replicas": 5,
    "updatedReplicas": 0,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifestWithPartition(1), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifestJson = baseJson << statusJson
    def manifest = stringToManifest(manifestJson)
    def status = handler.status(manifest)

    then:
    !status.stable.state
    status.stable.message == "Waiting for partitioned roll out to finish"
  }

  def "[status] wait for updated replicas"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 1,
    "currentReplicas": 5,
    "readyReplicas": 5,
    "replicas": 5,
    "updatedReplicas": 3,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifestWithPartition(1), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifestJson = baseJson << statusJson
    def manifest = stringToManifest(manifestJson)
    def status = handler.status(manifest)

    then:
    !status.stable.state
    status.stable.message == "Waiting for partitioned roll out to finish"
  }

  def "[status] wait for partitioned roll out complete"() {
    when:
    def statusJSONString = """
{
  "status": {
    "availableReplicas": 0,
    "observedGeneration": 1,
    "currentReplicas": 5,
    "readyReplicas": 5,
    "replicas": 5,
    "updatedReplicas": 5,
    "currentRevision": "$NAME-$VERSION",
    "updateRevision": "$NAME-$VERSION"
  }
}
""".toString()
    def baseJson = gsonObj.fromJson(statefulSetManifestWithPartition(), Object)
    def statusJson = gsonObj.fromJson(statusJSONString, Object)
    def manifestJson = baseJson << statusJson
    def manifest = stringToManifest(manifestJson)
    def status = handler.status(manifest)

    then:
    status.stable.state
    status.stable.message == "Partitioned roll out complete"
  }

}
