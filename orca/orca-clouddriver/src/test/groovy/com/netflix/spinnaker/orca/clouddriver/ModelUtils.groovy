package com.netflix.spinnaker.orca.clouddriver

import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.model.EntityTags
import com.netflix.spinnaker.orca.clouddriver.model.Instance
import com.netflix.spinnaker.orca.clouddriver.model.Instance.InstanceInfo
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper

class ModelUtils {

  static Instance instance(instance) {
    OrcaObjectMapper.instance.convertValue(instance, Instance)
  }

  static InstanceInfo instanceInfo(info) {
    OrcaObjectMapper.instance.convertValue(info, InstanceInfo)
  }

  static ServerGroup serverGroup(serverGroup) {
    OrcaObjectMapper.instance.convertValue(serverGroup, ServerGroup)
  }

  static Cluster cluster(cluster) {
    OrcaObjectMapper.instance.convertValue(cluster, Cluster)
  }

  static EntityTags tags(tags) {
    OrcaObjectMapper.instance.convertValue(tags, EntityTags)
  }
}
