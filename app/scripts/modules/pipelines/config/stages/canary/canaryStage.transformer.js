'use strict';

// TODO: Clean this up on the backend - this is a mess
angular.module('deckApp.pipelines.stage.canary.transformer', [])
  .service('canaryStageTransformer', function() {
    this.transform = function(application, execution) {
      var syntheticStagesToAdd = [];
      execution.stages.forEach(function(stage) {
        if (stage.type === 'canary') {
          var deployParent = _.find(execution.stages, {
            type: 'deployCanary',
            context: {
              canaryStageId: stage.id,
            }
          });
          var monitorStage = _.find(execution.stages, {
            type: 'monitorCanary',
            context: {
              canaryStageId: stage.id,
            }
          });

          stage.context.canary = monitorStage.context.canary || deployParent.context.canary || stage.context.canary;
          if (!stage.context.canary.canaryDeployments) {
            stage.context.canary.canaryDeployments = _.map(stage.context.clusterPairs, function(pair) {
              var name = function(cluster) {
                var parts = [cluster.application];
                if (cluster.stack) {
                  parts.push(cluster.stack);
                } else if (cluster.freeFormDetails) {
                  parts.push('');
                }
                if (cluster.freeFormDetails) {
                  parts.push(cluster.freeFormDetails);
                }
                return parts.join('-');
              };
              var region = function(cluster) {
                return _.first(_.keys(cluster.availabilityZones));
              };
              return {
                canaryCluster: {
                  accountName: pair.canary.account,
                  imageId: null,
                  buildId: null,
                  name: name(pair.canary),
                  region: region(pair.canary),
                  type: 'aws',
                },
                baselineCluster: {
                  accountName: pair.baseline.account,
                  imageId: pair.baseline.amiName,
                  buildId: pair.baseline.buildUrl,
                  name: name(pair.baseline),
                  region: region(pair.baseline),
                  type: 'aws',
                }
              };
            });
          }
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
          } else {
            stage.context.canary.status = { status: status };
          }

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
            id: monitorStage.id,
            name: monitorStage.name,
            startTime: monitorStage.startTime,
            endTime: monitorStage.endTime,
            status: monitorStage.status,
          });

          stage.tasks = tasks;

          stage.context.canary.canaryDeployments.forEach(function(deployment, deploymentIdx) {

            deployment.id = deployment.id || deploymentIdx;

            var deployedClusterPair = _.find(monitorStage.context.deployedClusterPairs, {
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
            var monitorTask = _.find(monitorStage.tasks, { name: 'monitorCanary' });
            if (monitorTask) {
              deploymentEndTime = monitorTask.endTime;
            }

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
            } else {
              deployment.baselineCluster.capacity = 'n/a';
              deployment.canaryCluster.capacity = 'n/a';
            }

            var canaryDeploymentId = deployment.canaryAnalysisResult ? deployment.canaryAnalysisResult.canaryDeploymentId : null;

            syntheticStagesToAdd.push({
              parentStageId: stage.id,
              syntheticStageOwner: 'STAGE_BEFORE',
              id: stage.id + '-' + deployment.id,
              type: 'canaryDeployment',
              name: deployment.canaryCluster.region,
              status: status,
              startTime: deployParent.startTime,
              endTime: deploymentEndTime,
              context: {
                canaryDeploymentId: canaryDeploymentId,
                application: stage.context.canary.application || execution.application,
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
