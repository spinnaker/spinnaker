'use strict';

angular.module('deckApp.delivery.executions.service', [
  'ui.router',
  'deckApp.scheduler',
  'deckApp.orchestratedItem.service',
  'deckApp.settings',
  'deckApp.utils.rx',
  'deckApp.utils.appendTransform',
  'deckApp.delivery.executionTransformer.service'
])
  .factory('executionsService', function($stateParams, $http, $timeout, $q, scheduler, orchestratedItem, settings, RxService, appendTransform, executionsTransformer) {

    function getExecutions(applicationName) {

      if (applicationName === 'deck') {
        var stub = [{'id':'b13733fc-f911-4858-9a6b-7ce5d1637ebc','application':'api','appConfig':{},'stages':[{'id':'6031ad52-080a-4bf8-ad01-e01c0731c980','type':'findAmi','name':'Find AMI','startTime':1429214364577,'endTime':1429214364773,'status':'SUCCEEDED','context':{'account':'test','amiDetails':[{'ami':'ami-0eb38966','architecture':'x86_64','blockDeviceMappings':[{'deviceName':'/dev/sda1','ebs':{'encrypted':false,'snapshotId':'snap-55019027','volumeSize':10,'volumeType':'standard'}},{'deviceName':'/dev/sdb','virtualName':'ephemeral0'},{'deviceName':'/dev/sdc','virtualName':'ephemeral1'},{'deviceName':'/dev/sdd','virtualName':'ephemeral2'},{'deviceName':'/dev/sde','virtualName':'ephemeral3'}],'commit':'301c401','description':'name=api, arch=x86_64, ancestor_name=trustybase-x86_64-201503201659-ebs, ancestor_id=ami-902d02f8, ancestor_version=nflx-base-1.445-h396.40e956d','hypervisor':'xen','imageId':'ami-0eb38966','imageLocation':'179727101194/api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','imageName':'api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','imageType':'machine','jenkins':{'host':'http://edge.builds.test.netflix.net/','name':'EDGE-Master-Family-Build','number':'3023'},'kernelId':'aki-919dcaf8','name':'api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','ownerId':'179727101194','package_name':'api','productCodes':[],'public':false,'region':'us-east-1','rootDeviceName':'/dev/sda1','rootDeviceType':'ebs','sourceServerGroup':'api-int-v287','state':'available','tags':[{'key':'appversion','value':'api-4.2349-h3023.301c401/EDGE-Master-Family-Build/3023'},{'key':'base_ami_version','value':'nflx-base-1.445-h396.40e956d'},{'key':'build_host','value':'http://edge.builds.test.netflix.net/'},{'key':'creation_time','value':'2015-04-16 19:00:55 UTC'},{'key':'creator','value':'builds'}],'version':'4.2349','virtualizationType':'paravirtual'}],'batch.task.id.findAmi':59,'cluster':'api-int','onlyEnabled':true,'regions':['us-east-1'],'selectionStrategy':'NEWEST'},'immutable':false,'initializationStage':false,'tasks':[{'id':'1','name':'findAmi','startTime':1429214364579,'endTime':1429214364771,'status':'SUCCEEDED'}],'parentStageId':null,'refId':null,'requisiteStageRefIds':null,'syntheticStageOwner':null,'scheduledTime':0},{'id':'ad110ae3-26a2-4f3b-9fc4-bc55513a3bde','type':'canary','name':'Canary','startTime':null,'endTime':null,'status':'NOT_STARTED','context':{'baseline':{'account':'test','cluster':'api-int'},'canaries':[{'account':'prod','application':'api','availabilityZones':{'us-east-1':['us-east-1c','us-east-1d','us-east-1e']},'capacity':{'desired':1,'max':1,'min':1},'cooldown':10,'dirty':{'vpcId':true},'ebsOptimized':false,'freeFormDetails':'sthadeshwar','healthCheckGracePeriod':600,'healthCheckType':'EC2','iamRole':'BaseIAMRole','instanceMonitoring':false,'instanceType':'m2.4xlarge','keyPair':'nf-prod-keypair-a','loadBalancers':[],'securityGroups':['sg-42c0132b','sg-ae9a5ec7','sg-31cd0758'],'stack':'prod','strategy':'highlander','suspendedProcesses':[],'terminationPolicies':['Default']}],'canaryConfig':{'canaryAnalysisConfig':{'beginCanaryAnalysisAfterMins':'30','canaryAnalysisIntervalMinutes':'30','name':'sthadeshwar','notificationHours':null},'canaryHealthCheckHandler':{'minimumCanaryResultScore':'70'},'canarySuccessCriteria':{'canaryResultScore':'90'},'combinedCanaryResultStrategy':'LOWEST','lifetimeHours':'5'},'owner':{'email':'sthadeshwar@netflix.com','name':'Satyajit Thadeshwar'},'scaleUp':{'capacity':'3','delay':'30','enabled':true},'watchers':[]},'immutable':false,'initializationStage':false,'tasks':[{'id':'1','name':'registerCanary','startTime':null,'endTime':null,'status':'NOT_STARTED'},{'id':'2','name':'monitorCanary','startTime':null,'endTime':null,'status':'NOT_STARTED'},{'id':'3','name':'cleanupCanary','startTime':null,'endTime':null,'status':'NOT_STARTED'},{'id':'4','name':'monitorCleanup','startTime':null,'endTime':null,'status':'NOT_STARTED'},{'id':'5','name':'completeCanary','startTime':null,'endTime':null,'status':'NOT_STARTED'}],'parentStageId':null,'refId':null,'requisiteStageRefIds':null,'syntheticStageOwner':null,'scheduledTime':0},{'id':'ad110ae3-26a2-4f3b-9fc4-bc55513a3bde-1-DeployCanary','type':'deployCanary','name':'Deploy Canary','startTime':1429214364782,'endTime':1429214364940,'status':'SUCCEEDED','context':{'baseline':{'account':'test','cluster':'api-int'},'batch.task.id.beginParallel':60,'batch.task.id.setupParallelDeploy':62,'canaryConfig':{'canaryAnalysisConfig':{'beginCanaryAnalysisAfterMins':'30','canaryAnalysisIntervalMinutes':'30','name':'sthadeshwar','notificationHours':null},'canaryHealthCheckHandler':{'minimumCanaryResultScore':'70'},'canarySuccessCriteria':{'canaryResultScore':'90'},'combinedCanaryResultStrategy':'LOWEST','lifetimeHours':'5'},'deploymentDetails':[{'ami':'ami-0eb38966','architecture':'x86_64','blockDeviceMappings':[{'deviceName':'/dev/sda1','ebs':{'encrypted':false,'snapshotId':'snap-55019027','volumeSize':10,'volumeType':'standard'}},{'deviceName':'/dev/sdb','virtualName':'ephemeral0'},{'deviceName':'/dev/sdc','virtualName':'ephemeral1'},{'deviceName':'/dev/sdd','virtualName':'ephemeral2'},{'deviceName':'/dev/sde','virtualName':'ephemeral3'}],'commit':'301c401','description':'name=api, arch=x86_64, ancestor_name=trustybase-x86_64-201503201659-ebs, ancestor_id=ami-902d02f8, ancestor_version=nflx-base-1.445-h396.40e956d','hypervisor':'xen','imageId':'ami-0eb38966','imageLocation':'179727101194/api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','imageName':'api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','imageType':'machine','jenkins':{'host':'http://edge.builds.test.netflix.net/','name':'EDGE-Master-Family-Build','number':'3023'},'kernelId':'aki-919dcaf8','name':'api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','ownerId':'179727101194','package_name':'api','productCodes':[],'public':false,'region':'us-east-1','rootDeviceName':'/dev/sda1','rootDeviceType':'ebs','sourceServerGroup':'api-int-v287','state':'available','tags':[{'key':'appversion','value':'api-4.2349-h3023.301c401/EDGE-Master-Family-Build/3023'},{'key':'base_ami_version','value':'nflx-base-1.445-h396.40e956d'},{'key':'build_host','value':'http://edge.builds.test.netflix.net/'},{'key':'creation_time','value':'2015-04-16 19:00:55 UTC'},{'key':'creator','value':'builds'}],'version':'4.2349','virtualizationType':'paravirtual'}],'owner':{'email':'sthadeshwar@netflix.com','name':'Satyajit Thadeshwar'},'scaleUp':{'capacity':'3','delay':'30','enabled':true},'watchers':[]},'immutable':false,'initializationStage':true,'tasks':[{'id':'1','name':'setupParallelDeploy','startTime':null,'endTime':1429214364938,'status':'SUCCEEDED'},{'id':'2','name':'setupParallelDeploy','startTime':1429214364857,'endTime':1429214364924,'status':'SUCCEEDED'},{'id':'3','name':'beginParallel','startTime':1429214364786,'endTime':1429214364842,'status':'SUCCEEDED'},{'id':'4','name':'completeParallel','startTime':null,'endTime':null,'status':'NOT_STARTED'}],'parentStageId':'ad110ae3-26a2-4f3b-9fc4-bc55513a3bde','refId':null,'requisiteStageRefIds':null,'syntheticStageOwner':'STAGE_BEFORE','scheduledTime':0},{'id':'ad110ae3-26a2-4f3b-9fc4-bc55513a3bde-2-Deployinuseast1','type':'deploy','name':'Deploy in us-east-1','startTime':1429214364950,'endTime':null,'status':'RUNNING','context':{'account':'prod','amiName':'ami-0eb38966','application':'api','availabilityZones':{'us-east-1':['us-east-1c','us-east-1d','us-east-1e']},'baseline':{'account':'test','cluster':'api-int'},'batch.task.id.createDeploy':64,'batch.task.id.forceCacheRefresh':67,'batch.task.id.monitorDeploy':65,'canaryConfig':{'canaryAnalysisConfig':{'beginCanaryAnalysisAfterMins':'30','canaryAnalysisIntervalMinutes':'30','name':'sthadeshwar','notificationHours':null},'canaryHealthCheckHandler':{'minimumCanaryResultScore':'70'},'canarySuccessCriteria':{'canaryResultScore':'90'},'combinedCanaryResultStrategy':'LOWEST','lifetimeHours':'5'},'capacity':{'desired':1,'max':1,'min':1},'cooldown':10,'deploy.account.name':'prod','deploy.server.groups':{'us-east-1':['api-prod-sthadeshwar_baseline-v000']},'deploymentDetails':[{'ami':'ami-0eb38966','architecture':'x86_64','blockDeviceMappings':[{'deviceName':'/dev/sda1','ebs':{'encrypted':false,'snapshotId':'snap-55019027','volumeSize':10,'volumeType':'standard'}},{'deviceName':'/dev/sdb','virtualName':'ephemeral0'},{'deviceName':'/dev/sdc','virtualName':'ephemeral1'},{'deviceName':'/dev/sdd','virtualName':'ephemeral2'},{'deviceName':'/dev/sde','virtualName':'ephemeral3'}],'commit':'301c401','description':'name=api, arch=x86_64, ancestor_name=trustybase-x86_64-201503201659-ebs, ancestor_id=ami-902d02f8, ancestor_version=nflx-base-1.445-h396.40e956d','hypervisor':'xen','imageId':'ami-0eb38966','imageLocation':'179727101194/api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','imageName':'api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','imageType':'machine','jenkins':{'host':'http://edge.builds.test.netflix.net/','name':'EDGE-Master-Family-Build','number':'3023'},'kernelId':'aki-919dcaf8','name':'api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','ownerId':'179727101194','package_name':'api','productCodes':[],'public':false,'region':'us-east-1','rootDeviceName':'/dev/sda1','rootDeviceType':'ebs','sourceServerGroup':'api-int-v287','state':'available','tags':[{'key':'appversion','value':'api-4.2349-h3023.301c401/EDGE-Master-Family-Build/3023'},{'key':'base_ami_version','value':'nflx-base-1.445-h396.40e956d'},{'key':'build_host','value':'http://edge.builds.test.netflix.net/'},{'key':'creation_time','value':'2015-04-16 19:00:55 UTC'},{'key':'creator','value':'builds'}],'version':'4.2349','virtualizationType':'paravirtual'}],'dirty':{'vpcId':true},'ebsOptimized':false,'freeFormDetails':'sthadeshwar_baseline','healthCheckGracePeriod':600,'healthCheckType':'EC2','iamRole':'BaseIAMRole','instanceMonitoring':false,'instanceType':'m2.4xlarge','kato.last.task.id':{'id':'133554'},'kato.task.id':{'id':'133554'},'kato.tasks':[{'history':[{'phase':'ORCHESTRATION','status':'Initializing Orchestration Task...'},{'phase':'ORCHESTRATION','status':'Processing op: AllowLaunchAtomicOperation'},{'phase':'ALLOW_LAUNCH','status':'Initializing Allow Launch Operation...'},{'phase':'ALLOW_LAUNCH','status':'Allowing launch of ami-0eb38966 from prod'},{'phase':'ALLOW_LAUNCH','status':'Done allowing launch of ami-0eb38966 from prod.'},{'phase':'ORCHESTRATION','status':'Orchestration completed.'},{'phase':'ORCHESTRATION','status':'Processing op: DeployAtomicOperation'},{'phase':'DEPLOY','status':'Initializing phase.'},{'phase':'DEPLOY','status':'Looking for BasicAmazonDeployDescription handler...'},{'phase':'DEPLOY','status':'Found handler: BasicAmazonDeployHandler'},{'phase':'DEPLOY','status':'Invoking Handler.'},{'phase':'DEPLOY','status':'Initializing handler...'},{'phase':'DEPLOY','status':'Preparing deployment to [us-east-1:[us-east-1c, us-east-1d, us-east-1e]]...'},{'phase':'AWS_DEPLOY','status':'Beginning Amazon deployment.'},{'phase':'AWS_DEPLOY','status':'Looking up security groups...'},{'phase':'AWS_DEPLOY','status':'Beginning ASG deployment.'},{'phase':'AWS_DEPLOY','status':'Building launch configuration for new ASG.'},{'phase':'AWS_DEPLOY','status':'Deploying ASG.'},{'phase':'AWS_DEPLOY','status':'Deploying to availabilityZones: [us-east-1c, us-east-1d, us-east-1e]'},{'phase':'DEPLOY','status':'Server Groups: [us-east-1:api-prod-sthadeshwar_baseline-v000] created.'},{'phase':'ORCHESTRATION','status':'Orchestration completed.'}],'id':'133554','resultObjects':[{'amiId':'ami-0eb38966','amiName':'ami-0eb38966','region':'us-east-1'},{'asgNameByRegion':{'us-east-1':'api-prod-sthadeshwar_baseline-v000'},'messages':[],'serverGroupNames':['us-east-1:api-prod-sthadeshwar_baseline-v000']}],'status':{'completed':true,'failed':false}}],'keyPair':'nf-prod-keypair-a','loadBalancers':[],'name':{'bytes':'RGVwbG95IGluIHVzLWVhc3QtMQ==','strings':['Deploy in ',''],'valueCount':1,'values':['us-east-1']},'notification.type':'createdeploy','owner':{'email':'sthadeshwar@netflix.com','name':'Satyajit Thadeshwar'},'scaleUp':{'capacity':'3','delay':'30','enabled':true},'securityGroups':['sg-42c0132b','sg-ae9a5ec7','sg-31cd0758'],'stack':'prod','strategy':'highlander','suspendedProcesses':[],'terminationPolicies':['Default'],'type':'linearDeploy','watchers':[]},'immutable':false,'initializationStage':false,'tasks':[{'id':'1','name':'createDeploy','startTime':1429214364952,'endTime':1429214365305,'status':'SUCCEEDED'},{'id':'2','name':'monitorDeploy','startTime':1429214365328,'endTime':1429214398528,'status':'SUCCEEDED'},{'id':'3','name':'forceCacheRefresh','startTime':1429214398559,'endTime':1429214399265,'status':'SUCCEEDED'},{'id':'4','name':'waitForUpInstances','startTime':1429214399281,'endTime':null,'status':'RUNNING'},{'id':'5','name':'forceCacheRefresh','startTime':null,'endTime':null,'status':'NOT_STARTED'}],'parentStageId':'ad110ae3-26a2-4f3b-9fc4-bc55513a3bde-1-DeployCanary','refId':null,'requisiteStageRefIds':null,'syntheticStageOwner':'STAGE_AFTER','scheduledTime':0},{'id':'ad110ae3-26a2-4f3b-9fc4-bc55513a3bde-3-Deployinuseast1','type':'deploy','name':'Deploy in us-east-1','startTime':1429214364935,'endTime':null,'status':'RUNNING','context':{'account':'prod','application':'api','availabilityZones':{'us-east-1':['us-east-1c','us-east-1d','us-east-1e']},'baseline':{'account':'test','cluster':'api-int'},'batch.task.id.createDeploy':63,'batch.task.id.forceCacheRefresh':69,'batch.task.id.monitorDeploy':66,'canaryConfig':{'canaryAnalysisConfig':{'beginCanaryAnalysisAfterMins':'30','canaryAnalysisIntervalMinutes':'30','name':'sthadeshwar','notificationHours':null},'canaryHealthCheckHandler':{'minimumCanaryResultScore':'70'},'canarySuccessCriteria':{'canaryResultScore':'90'},'combinedCanaryResultStrategy':'LOWEST','lifetimeHours':'5'},'capacity':{'desired':1,'max':1,'min':1},'cooldown':10,'deploy.account.name':'prod','deploy.server.groups':{'us-east-1':['api-prod-sthadeshwar_canary-v000']},'deploymentDetails':[{'ami':'ami-0eb38966','architecture':'x86_64','blockDeviceMappings':[{'deviceName':'/dev/sda1','ebs':{'encrypted':false,'snapshotId':'snap-55019027','volumeSize':10,'volumeType':'standard'}},{'deviceName':'/dev/sdb','virtualName':'ephemeral0'},{'deviceName':'/dev/sdc','virtualName':'ephemeral1'},{'deviceName':'/dev/sdd','virtualName':'ephemeral2'},{'deviceName':'/dev/sde','virtualName':'ephemeral3'}],'commit':'301c401','description':'name=api, arch=x86_64, ancestor_name=trustybase-x86_64-201503201659-ebs, ancestor_id=ami-902d02f8, ancestor_version=nflx-base-1.445-h396.40e956d','hypervisor':'xen','imageId':'ami-0eb38966','imageLocation':'179727101194/api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','imageName':'api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','imageType':'machine','jenkins':{'host':'http://edge.builds.test.netflix.net/','name':'EDGE-Master-Family-Build','number':'3023'},'kernelId':'aki-919dcaf8','name':'api-4.2349-h3023.301c401-x86_64-201504161854-trusty-pv-ebs','ownerId':'179727101194','package_name':'api','productCodes':[],'public':false,'region':'us-east-1','rootDeviceName':'/dev/sda1','rootDeviceType':'ebs','sourceServerGroup':'api-int-v287','state':'available','tags':[{'key':'appversion','value':'api-4.2349-h3023.301c401/EDGE-Master-Family-Build/3023'},{'key':'base_ami_version','value':'nflx-base-1.445-h396.40e956d'},{'key':'build_host','value':'http://edge.builds.test.netflix.net/'},{'key':'creation_time','value':'2015-04-16 19:00:55 UTC'},{'key':'creator','value':'builds'}],'version':'4.2349','virtualizationType':'paravirtual'}],'dirty':{'vpcId':true},'ebsOptimized':false,'freeFormDetails':'sthadeshwar_canary','healthCheckGracePeriod':600,'healthCheckType':'EC2','iamRole':'BaseIAMRole','instanceMonitoring':false,'instanceType':'m2.4xlarge','kato.last.task.id':{'id':'133553'},'kato.task.id':{'id':'133553'},'kato.tasks':[{'history':[{'phase':'ORCHESTRATION','status':'Initializing Orchestration Task...'},{'phase':'ORCHESTRATION','status':'Processing op: AllowLaunchAtomicOperation'},{'phase':'ALLOW_LAUNCH','status':'Initializing Allow Launch Operation...'},{'phase':'ALLOW_LAUNCH','status':'Allowing launch of ami-0eb38966 from prod'},{'phase':'ALLOW_LAUNCH','status':'Done allowing launch of ami-0eb38966 from prod.'},{'phase':'ORCHESTRATION','status':'Orchestration completed.'},{'phase':'ORCHESTRATION','status':'Processing op: DeployAtomicOperation'},{'phase':'DEPLOY','status':'Initializing phase.'},{'phase':'DEPLOY','status':'Looking for BasicAmazonDeployDescription handler...'},{'phase':'DEPLOY','status':'Found handler: BasicAmazonDeployHandler'},{'phase':'DEPLOY','status':'Invoking Handler.'},{'phase':'DEPLOY','status':'Initializing handler...'},{'phase':'DEPLOY','status':'Preparing deployment to [us-east-1:[us-east-1c, us-east-1d, us-east-1e]]...'},{'phase':'AWS_DEPLOY','status':'Beginning Amazon deployment.'},{'phase':'AWS_DEPLOY','status':'Looking up security groups...'},{'phase':'AWS_DEPLOY','status':'Beginning ASG deployment.'},{'phase':'AWS_DEPLOY','status':'Building launch configuration for new ASG.'},{'phase':'AWS_DEPLOY','status':'Deploying ASG.'},{'phase':'AWS_DEPLOY','status':'Deploying to availabilityZones: [us-east-1c, us-east-1d, us-east-1e]'},{'phase':'DEPLOY','status':'Server Groups: [us-east-1:api-prod-sthadeshwar_canary-v000] created.'},{'phase':'ORCHESTRATION','status':'Orchestration completed.'}],'id':'133553','resultObjects':[{'amiId':'ami-0eb38966','amiName':'ami-0eb38966','region':'us-east-1'},{'asgNameByRegion':{'us-east-1':'api-prod-sthadeshwar_canary-v000'},'messages':[],'serverGroupNames':['us-east-1:api-prod-sthadeshwar_canary-v000']}],'status':{'completed':true,'failed':false}}],'keyPair':'nf-prod-keypair-a','loadBalancers':[],'name':{'bytes':'RGVwbG95IGluIHVzLWVhc3QtMQ==','strings':['Deploy in ',''],'valueCount':1,'values':['us-east-1']},'notification.type':'createdeploy','owner':{'email':'sthadeshwar@netflix.com','name':'Satyajit Thadeshwar'},'scaleUp':{'capacity':'3','delay':'30','enabled':true},'securityGroups':['sg-42c0132b','sg-ae9a5ec7','sg-31cd0758'],'stack':'prod','strategy':'highlander','suspendedProcesses':[],'terminationPolicies':['Default'],'type':'linearDeploy','watchers':[]},'immutable':false,'initializationStage':false,'tasks':[{'id':'1','name':'createDeploy','startTime':1429214364937,'endTime':1429214365312,'status':'SUCCEEDED'},{'id':'2','name':'monitorDeploy','startTime':1429214365333,'endTime':1429214399607,'status':'SUCCEEDED'},{'id':'3','name':'forceCacheRefresh','startTime':1429214399630,'endTime':1429214400325,'status':'SUCCEEDED'},{'id':'4','name':'waitForUpInstances','startTime':1429214400348,'endTime':null,'status':'RUNNING'},{'id':'5','name':'forceCacheRefresh','startTime':null,'endTime':null,'status':'NOT_STARTED'}],'parentStageId':'ad110ae3-26a2-4f3b-9fc4-bc55513a3bde-1-DeployCanary','refId':null,'requisiteStageRefIds':null,'syntheticStageOwner':'STAGE_AFTER','scheduledTime':0}],'canceled':false,'parallel':false,'name':'Testing Canary','pipelineConfigId':'4f0eeb60-e472-11e4-ae15-5107b26a5b34','trigger':{'enabled':true,'type':'manual','master':'edge','job':'EDGE-Master-Family-Build','buildNumber':3020,'description':'edge: EDGE-Master-Family-Build','user':'[anonymous]','buildInfo':{'building':false,'number':3020.0,'result':'SUCCESS','timestamp':'1429163809379','duration':359699.0,'url':'http://edge.builds.test.netflix.net/job/EDGE-Master-Family-Build/3020/','artifacts':[{'fileName':'api-4.2346-h3020.5c815ec.txt','displayPath':'api-4.2346-h3020.5c815ec.txt','relativePath':'apiweb/build/api-4.2346-h3020.5c815ec.txt'},{'fileName':'api_4.2346-h3020.5c815ec_all.txt','displayPath':'api_4.2346-h3020.5c815ec_all.txt','relativePath':'apiweb/build/api_4.2346-h3020.5c815ec_all.txt'},{'fileName':'deb.properties','displayPath':'deb.properties','relativePath':'apiweb/build/deb.properties'},{'fileName':'api_4.2346-h3020.5c815ec_all.deb','displayPath':'api_4.2346-h3020.5c815ec_all.deb','relativePath':'apiweb/build/distributions/api_4.2346-h3020.5c815ec_all.deb'},{'fileName':'dependencies.lock','displayPath':'dependencies.lock','relativePath':'apiweb/dependencies.lock'}],'testResults':[{'failCount':0.0,'skipCount':9.0,'totalCount':455.0,'urlName':'testReport'}],'scm':{'ref':'refs/remotes/origin/master-dependency-update','branch':'master-dependency-update','sha1':'5c815ec637d7cb4e13a28ae0e995bd7b0bd3d848'}}},'initialConfig':{},'endTime':null,'status':'RUNNING','startTime':1429214364577}];

        stub.forEach(executionsTransformer.transformExecution);
        return $q.when(stub);
      }

      var deferred = $q.defer();
      $http({
        method: 'GET',
        transformResponse: appendTransform(function(executions) {
          if (!executions || !executions.length) {
            return [];
          }
          executions.forEach(executionsTransformer.transformExecution);
          return executions;
        }),
        url: [
          settings.gateUrl,
          'applications',
          applicationName,
          'pipelines',
        ].join('/'),
      }).then(
        function(resp) {
          deferred.resolve(resp.data);
        },
        function(resp) {
          deferred.reject(resp);
        }
      );
      return deferred.promise;
    }

    function waitUntilNewTriggeredPipelineAppears(application, pipelineName, triggeredPipelineId) {

      return application.reloadExecutions().then(function() {
        var executions = application.executions;
        var match = executions.filter(function(execution) {
          return execution.id === triggeredPipelineId;
        });
        var deferred = $q.defer();
        if (match && match.length) {
          deferred.resolve();
          return deferred.promise;
        } else {
          return $timeout(function() {
            return waitUntilNewTriggeredPipelineAppears(application, pipelineName, triggeredPipelineId);
          }, 1000);
        }
      });
    }

    function cancelExecution(executionId) {
      var deferred = $q.defer();
      $http({
        method: 'PUT',
        url: [
          settings.gateUrl,
          'applications',
          $stateParams.application,
          'pipelines',
          executionId,
          'cancel',
        ].join('/')
      }).then(
          function() {
            scheduler.scheduleImmediate();
            deferred.resolve();
          },
          function(exception) {
            deferred.reject(exception && exception.data ? exception.message : null);
          }
        );
      return deferred.promise;
    }

    function deleteExecution(application, executionId) {
      var deferred = $q.defer();
      $http({
        method: 'DELETE',
        url: [
          settings.gateUrl,
          'pipelines',
          executionId,
        ].join('/')
      }).then(
        function() {
          application.reloadExecutions().then(deferred.resolve);
        },
        function(exception) {
          deferred.reject(exception && exception.data ? exception.data.message : null);
        }
      );
      return deferred.promise;
    }

    function getSectionCacheKey(groupBy, application, heading) {
      return ['pipeline', groupBy, application, heading].join('#');
    }

    return {
      getAll: getExecutions,
      cancelExecution: cancelExecution,
      deleteExecution: deleteExecution,
      forceRefresh: scheduler.scheduleImmediate,
      subscribeAll: function(fn) {
        return scheduler
          .get()
          .flatMap(function() {
            return RxService.Observable.fromPromise(getExecutions($stateParams.application));
          })
          .subscribe(fn);
      },
      waitUntilNewTriggeredPipelineAppears: waitUntilNewTriggeredPipelineAppears,
      getSectionCacheKey: getSectionCacheKey,
    };
  });
