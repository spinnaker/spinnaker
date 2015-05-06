'use strict';

angular.module('deckApp.pipelines.stage.canary.transformer', [])
  .service('canaryStageTransformer', function() {
    this.transform = function(application, execution) {
      var syntheticStagesToAdd = [];
      execution.stages.forEach(function(stage) {
        if (stage.type === 'monitorCanary' && stage.context && stage.context.canary && stage.context.canary.canaryDeployments) {
          var status = 'UNKNOWN';
          var canaryStatus = stage.context.canary.status;
          if (canaryStatus) {
            if (canaryStatus.status === 'LAUNCHED') {
              status = 'RUNNING';
            }
            if (canaryStatus.complete) {
              status = 'COMPLETED';
            }
            canaryStatus.status = status;
          }

          var deployParent = _.find(execution.stages, {
            type: 'deployCanary',
            context: {
              canaryStageId: stage.context.canaryStageId,
            }
          });

          var deployStages = _.filter(execution.stages, {
            type: 'deploy',
            parentStageId: deployParent.id,
          });

          var tasks = _.map(deployStages, function(deployStage) {
            return {
              id: deployStage.id,
              name: deployStage.name,
              startTime: deployStage.startTime,
              endTime: deployStage.endTime,
              status: deployStage.status,
            };
          });

          tasks.push({
            id: stage.id,
            name: stage.name,
            startTime: stage.startTime,
            endTime: stage.endTime,
            status: stage.status,
          });

          var canaryStage = _.find(execution.stages, {
            id: stage.context.canaryStageId,
          });

          canaryStage.tasks = tasks;



          stage.context.canary.canaryDeployments.forEach(function(deployment) {
            if (!deployment.baselineCluster || !stage.context.deployedClusterPairs) {
              return;
            }

            var deployedClusterPair = _.find(stage.context.deployedClusterPairs, {
              baselineCluster: {
                accountName: deployment.baselineCluster.accountName,
                name: deployment.baselineCluster.name,
                region: deployment.baselineCluster.region,
              },
              canaryCluster: {
                accountName: deployment.canaryCluster.accountName,
                name: deployment.canaryCluster.name,
                region: deployment.canaryCluster.region,
              }
            });

            var deploymentEndTime = null;
            var monitorTask = _.find(stage.tasks, { name: 'monitorCanary' });
            if (monitorTask) {
              deploymentEndTime = monitorTask.endTime;
            }

            // TODO: Clean this up on the backend - this is a mess
            var extractBuild = function(cluster) {
              cluster.build = cluster.build || {};
              if (cluster.buildId) {
                var parts = cluster.buildId.split('/');
                parts.pop();
                cluster.build.url = cluster.buildId;
                cluster.build.number = parts.pop();
              } else {
                cluster.build.url = '#';
                cluster.build.number = 'n/a';
              }
            };
            extractBuild(deployment.baselineCluster);
            extractBuild(deployment.canaryCluster);

            deployment.canaryResult = deployment.canaryAnalysisResult || {};
            deployment.canaryCluster = deployment.canaryCluster || {};

            if (deployedClusterPair) {
              var canaryServerGroup = _.find(application.serverGroups, {
                name: deployedClusterPair.canaryCluster.serverGroup,
                account: deployedClusterPair.canaryCluster.accountName,
                region: deployedClusterPair.canaryCluster.region
              });
              if (canaryServerGroup) {
                deployment.canaryCluster.capacity = canaryServerGroup.instances.length;
              } else {
                deployment.canaryCluster.capacity = 'n/a';
              }

              var baselineServerGroup = _.find(application.serverGroups, {
                name: deployedClusterPair.baselineCluster.serverGroup,
                account: deployedClusterPair.baselineCluster.accountName,
                region: deployedClusterPair.baselineCluster.region
              });
              if (baselineServerGroup) {
                deployment.baselineCluster.capacity = baselineServerGroup.instances.length;
              } else {
                deployment.baselineCluster.capacity = 'n/a';
              }
            }


            var canaryDeploymentId = deployment.canaryAnalysisResult ? deployment.canaryAnalysisResult.canaryDeploymentId : null;

            syntheticStagesToAdd.push({
              parentStageId: stage.id,
              syntheticStageOwner: 'STAGE_BEFORE',
              id: stage.id + '-' + deployment.id,
              type: 'canaryDeployment',
              name: deployment.canaryCluster.region,
              status: status,
              startTime: stage.startTime,
              endTime: deploymentEndTime,
              context: {
                canaryDeploymentId: canaryDeploymentId,
                application: stage.context.canary.application,
                canaryCluster: deployment.canaryCluster,
                baselineCluster: deployment.baselineCluster,
                canaryResult: deployment.canaryResult,
                lastUpdated: deployment.lastUpdated,
                status: {
                  reportUrl: deployment.canaryResult.canaryReportURL,
                  score: deployment.canaryResult.score,
                  result: deployment.canaryResult.result,
                  duration: deployment.canaryResult.timeDuration,
                  health: deployment.health ? deployment.health.health : '',
                }
              }
            });
          });
        }
      });
      execution.stages = execution.stages.concat(syntheticStagesToAdd);
    };
  });
