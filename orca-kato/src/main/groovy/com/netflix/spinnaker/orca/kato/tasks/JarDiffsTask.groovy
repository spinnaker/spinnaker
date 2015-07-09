package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.oort.util.OortHelper;
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RestAdapter
import retrofit.RetrofitError;


// FIXME: should be inserted after the new asg is up
// example url : http://ec2-54-155-148-19.eu-west-1.compute.amazonaws.com:8077/jars
// TODO: surround in a fat try/catch
class JarDiffsTask implements Task {
  @Autowired
  ObjectMapper objectMapper

  int platformPort = 8077

  @Override public TaskResult execute(Stage stage) {
    // figure out source + target asgs
    String region = stage.context?.source?.region ?: stage.context?.availabilityZones?.findResult { key, value -> key }
    String targetAsg = getTargetAsg(stage.context, region)
    String sourceAsg = getSourceAsg(stage.context, region)

    // get healthy instances from each
    ArrayList targetInstances = getHealthyInstancesFromAsg(targetAsg)
    ArrayList sourceInstances = getHealthyInstancesFromAsg(sourceAsg)

    // get jar json info
    def targetJarList = getJarList(targetInstances)
    String sourceJarList = getJarList(sourceInstances)

    // diff
    Map jarDiffs = diffJars(sourceJarList, targetJarList)

    // add the diffs to the context
    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [jarDiffs: jarDiffs])
  }

  Map diffJars(Map source, Map target) {
    def notInTarget = []
    def notInSource = []
    source.jars.each { jar ->
      // in source but not in target
      def targetJar = target.jars.findResult { it.name == jar.name ? it : null}
      if(!targetJar) {
        notInTarget << jar
      }
    }

    target.jars.each { jar ->
      // in target but not in source
      def sourceJar = source.jars.findResult { it.name == jar.name ? it : null}
      if(!sourceJar) {
        notInSource << jar
      }
    }
    return [ source : notInTarget, target : notInSource ]
  }

  InstanceService createInstanceService(String address) {
    RestAdapter restAdapter = new RestAdapter.Builder()
      .setEndpoint(address)
      .build()
    return restAdapter.create(InstanceService.class)
  }

  Map getJarList(Map instances) {
    Map jarMap = [:]

    instances.each { String key, Map valueMap ->
      String hostName = valueMap.hostName
      def instanceService = createInstanceService("http://${hostName}:${platformPort}")
      try {
        def instanceResponse = instanceService.getJars()
        jarMap = objectMapper.readValue(instanceResponse.body.in().text, Map)
        return true
      } catch(Exception e) {
        println "exception thrown ${e}"
        // swallow it so we can try the next instance
      }
    }
    return jarMap
  }

  Map getHealthyInstancesFromAsg(String asgName) {
    return new OortHelper().getInstancesForCluster(stage.context, asgName, false, false)
  }

  String getTargetAsg(Map context, String region) {
    if (context."kato.tasks") { // deploy asg stage
      return context.get("kato.tasks")?.find { item ->
        item.find { key, value ->
          key == 'resultObjects'
        }
      }?.resultObjects?.find { another ->
        another.find { key, value ->
          key == "serverGroupNameByRegion"
        }
      }?.serverGroupNameByRegion?.get(region)
    } else {
      return null
    }
  }

  String getSourceAsg(Map context, String region) {
    if (context."kato.tasks") { // deploy asg stage
      return context.get("kato.tasks")?.find { item ->
        item.find { key, value ->
          key == 'resultObjects'
        }
      }?.resultObjects?.find { another ->
        another.find { key, value ->
          key == "ancestorServerGroupNameByRegion"
        }
      }?.ancestorServerGroupNameByRegion?.get(region)
    } else {
      return null
    }
  }
}
