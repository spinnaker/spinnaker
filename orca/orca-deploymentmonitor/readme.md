# Deployment monitors

Deployment monitors are a key aspect of the monitored [deploy strategy](../orca-clouddriver/src/main/groovy/com/netflix/spinnaker/orca/clouddriver/pipeline/servergroup/strategies/MonitoredDeployStrategy.groovy).  
They are authored by a third party and provide input as to the health/status of the deployed instances during a monitored deploy.

### Deployment Monitor interface
The monitors must expose 3 endpoints. `orca` will call these endpoints at the relevant points during the deploy process.  
All calls will receive details about the current deployment (e.g. application name, old/new server group, etc).  
The deployment monitor is able to control the flow of the deploy as defined below

(See [DeploymentMonitorService.java](./src/main/java/com/netflix/spinnaker/orca/deploymentmonitor/DeploymentMonitorService.java) for details on the interface.) 

1. `POST: /deployment/starting`  
    `orca` will generate this request when the deployment process is starting.  
    The deployment monitor is able to abort the deployment at this point. This is useful when, say, the deployment monitor hasn't been configured to monitor this particular application. 

2. `POST: /deployment/completed`  
    `orca` will generate this request when the deployment process is complete.  
    This is purely informational, the responses from the deployment monitor will be ignored. 

3. `POST: /deployment/evaluateHealth`  
    `orca` will generate this request when a deployment process completes a step (e.g. 10%)    
    The deployment monitor is able to: proceed, stall, abort, or complete the deployment at this point.  
    For example, it might take ~10 minutes to evaluate the health of the deployed instances, during this time the deployment monitor should respond with [`wait`](./src/main/java/com/netflix/spinnaker/orca/deploymentmonitor/models/EvaluateHealthResponse.java) 
    and `orca` will retry in a little bit (with a maximum of `maxAnalysisMinutes` for the given monitor or, 30 minutes, if not defined).  
    If the health is determined to be poor, the monitor can return `abort` to terminate the deployment.   
    Finally, if the health is good, the deployment monitor can respond with `continue` or `complete` which will proceed with deployment or skip straight to deploying 100%, respectively.
    
    
Additionally, the deployment monitor can (and should - especially in failure cases) specify reasons for its decision under [StatusReason](./src/main/java/com/netflix/spinnaker/orca/deploymentmonitor/models/EvaluateHealthResponse.java). 
This information will be shown in the UI and will help the user understand why a deployment proceeded as it did.  
     


### Deployment Monitor registration
Deployment monitors must be registered in `orca.yml` in order to be used - this is a safety mechanism - we don't want `orca` hitting any random URLs during the deploy process.  

The registration takes the following form:

```yaml
monitoredDeploy:
  enabled: true
  deploymentMonitors:
    - id: id1
      name: LiveLogMonitor
      baseUrl: http://logmonitor.aws.com/spinnaker
      failOnError: false
      maxAnalysisMinutes: 40
    - id: id2
      name: LocalTestMonitore
      baseUrl: http://localhost:8080/v1
      failOnError: true
      maxAnalysisMinutes: 10
```

where:

|Parameter              | Meaning                          
|-----------------------|----------------------------------|
|`id`                   | ID of the monitor that is specifies in the stage JSON
|`name`                 | User friendly name of the monitor, to be displayed in `deck`
|`baseUrl`              | Base URL of the monitor API
|`failOnError`          | Indicates if failure to communicate with the monitor should be considered a failure (and deploy aborted). This should always be `true`
|`maxAnalysisMinutes`   | Maximum number of minutes that are allowed for health evaluation for this deployment monitor
