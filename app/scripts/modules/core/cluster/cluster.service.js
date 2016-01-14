'use strict';

/* eslint consistent-return:0 */

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cluster.service', [
  require('../naming/naming.service.js'),
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../utils/lodash.js'),
  require('../serverGroup/serverGroup.transformer.js'),
])
  .factory('clusterService', function ($q, Restangular, _, serverGroupTransformer, namingService) {

    function loadServerGroups(applicationName) {
      var serverGroupLoader = Restangular.one('applications', applicationName).all('serverGroups').getList();
      return serverGroupLoader.then(function(results) {
        results.forEach(addHealthStatusCheck);
        results.forEach(addStackToServerGroup);
        return $q.all(results.map(serverGroupTransformer.normalizeServerGroup));
      });
    }

    function addStackToServerGroup(serverGroup) {
      var nameParts = namingService.parseServerGroupName(serverGroup.name);
      serverGroup.stack = nameParts.stack;
      serverGroup.detail = nameParts.freeFormDetails;
    }

    function addHealthStatusCheck(serverGroup) {
      serverGroup.instances.forEach(function (instance) {
        var healthList = instance.health;
        var activeHealth = _.filter(healthList, function(health) {
          return health.state !== 'Unknown';
        });

        instance.hasHealthStatus = Boolean(activeHealth.length);
      });
    }

    function addHealthCountsToCluster(cluster) {
      cluster.instanceCounts = {
        up: 0,
        down: 0,
        unknown: 0,
        starting: 0,
        outOfService: 0,
        total: 0,
      };
      if (!cluster.serverGroups) {
        return;
      }
      cluster.serverGroups.forEach(function(serverGroup) {
        cluster.instanceCounts.total += serverGroup.instanceCounts.total || 0;
        cluster.instanceCounts.up += serverGroup.instanceCounts.up || 0;
        cluster.instanceCounts.down += serverGroup.instanceCounts.down || 0;
        cluster.instanceCounts.unknown += serverGroup.instanceCounts.unknown || 0;
        cluster.instanceCounts.starting += serverGroup.instanceCounts.starting || 0;
        cluster.instanceCounts.outOfService += serverGroup.instanceCounts.outOfService || 0;
      });
    }

    function baseTaskMatcher(task, serverGroup) {
      var taskRegion = task.getValueFor('regions') ? task.getValueFor('regions')[0] : null;
      return serverGroup.account === task.getValueFor('credentials') &&
        serverGroup.region === taskRegion &&
        serverGroup.name === task.getValueFor('asgName');
    }

    function instanceIdsTaskMatcher(task, serverGroup) {
      if (task.getValueFor('region') === serverGroup.region && task.getValueFor('credentials') === serverGroup.account) {
        if (task.getValueFor('knownInstanceIds')) {
          return _.intersection(_.pluck(serverGroup.instances, 'id'), task.getValueFor('knownInstanceIds')).length > 0;
        } else {
          return _.intersection(_.pluck(serverGroup.instances, 'id'), task.getValueFor('instanceIds')).length > 0;
        }
      }
      return false;
    }

    var taskMatchers = {
      'createcopylastasg': function(task, serverGroup) {
        var source = task.getValueFor('source');
        var targetAccount = task.getValueFor('deploy.account.name');
        var targetRegion = task.getValueFor('availabilityZones') ? Object.keys(task.getValueFor('availabilityZones'))[0] : null;
        var dsgs = task.getValueFor('deploy.server.groups');
        var targetServerGroup = targetRegion && dsgs && dsgs[targetRegion] ? dsgs[targetRegion][0] : null;
        var sourceServerGroup = source.asgName;
        var sourceAccount = source.account;
        var sourceRegion = source.region;

        if (serverGroup.account === targetAccount && serverGroup.region === targetRegion && serverGroup.name === targetServerGroup) {
          return true;
        }
        if (serverGroup.account === sourceAccount && serverGroup.region === sourceRegion && serverGroup.name === sourceServerGroup) {
          return true;
        }
        return false;
      },
      'createdeploy': function(task, serverGroup) {
        var account = task.getValueFor('deploy.account.name'),
            region = task.getValueFor('deploy.server.groups') ? Object.keys(task.getValueFor('deploy.server.groups'))[0] : null,
            serverGroupName = (serverGroup && region) ? task.getValueFor('deploy.server.groups')[region][0] : null;

        if (account && serverGroup && region) {
          return serverGroup.account === account && serverGroup.region === region && serverGroup.name === serverGroupName;
        }
        return false;
      },
      'enableinstancesindiscovery': instanceIdsTaskMatcher,
      'disableinstancesindiscovery': instanceIdsTaskMatcher,
      'disableinstances': instanceIdsTaskMatcher,
      'registerinstanceswithloadbalancer': instanceIdsTaskMatcher,
      'deregisterinstancesfromloadbalancer': instanceIdsTaskMatcher,
      'terminateinstances': instanceIdsTaskMatcher,
      'rebootinstances': instanceIdsTaskMatcher,
      'resizeasg': baseTaskMatcher,
      'resizeservergroup': baseTaskMatcher,
      'disableasg': baseTaskMatcher,
      'disableservergroup': baseTaskMatcher,
      'destroyasg': baseTaskMatcher,
      'destroyservergroup': baseTaskMatcher,
      'enableasg': baseTaskMatcher,
      'enableservergroup': baseTaskMatcher,
      'enablegoogleservergroup': baseTaskMatcher,
      'disablegoogleservergroup': baseTaskMatcher,
      'rollbackServerGroup': function(task, serverGroup) {
        var account = task.getValueFor('credentials'),
            region = task.getValueFor('regions') ? task.getValueFor('regions')[0] : null;

        if (account && serverGroup.account === account && region && serverGroup.region === region) {
          return serverGroup.name === task.getValueFor('targetop.asg.disableServerGroup.name') ||
            serverGroup.name === task.getValueFor('targetop.asg.enableServerGroup.name');
        }
        return false;
      },
    };

    function taskMatches(task, serverGroup) {
      let notificationType = _.has(task, 'execution.stages') ?
        task.execution.stages[0].context['notification.type'] ?
          task.execution.stages[0].context['notification.type'] :
          task.execution.stages[0].type : // TODO: good grief
        task.getValueFor('notification.type');
      var matcher = taskMatchers[notificationType];
      return matcher ? matcher(task, serverGroup) : false;
    }

    function addTasksToServerGroups(application) {
      var runningTasks = _.where(application.tasks, {status: 'RUNNING'});
      if (!application.serverGroups) {
        return;
      }
      application.serverGroups.forEach(function(serverGroup) {
        serverGroup.runningTasks = [];
        runningTasks.forEach(function(task) {
          if (taskMatches(task, serverGroup)) {
            serverGroup.runningTasks.push(task);
          }
        });
      });
    }


    function findStagesWithServerGroupInfo(stages) {
      var stagesWithServerGroups = _.filter(stages, function (stage) {
         return ( _.includes(['deploy', 'destroyAsg', 'resizeAsg'], stage.type) && _.has(stage.context, 'deploy.server.groups') ) ||
           ( stage.type === 'disableAsg' && _.has(stage.context, 'targetop.asg.disableAsg.name') );
      });

      return stagesWithServerGroups;
    }

    function extractServerGroupNameFromContext(context) {
      return _.first(_.values(context['deploy.server.groups'])) || context['targetop.asg.disableAsg.name'] || undefined;
    }

    function extractRegionFromContext(context) {
      return _.first(_.keys(context['deploy.server.groups'])) || _.first(context['targetop.asg.disableAsg.regions']) || undefined;
    }

    function addExecutionsToServerGroups(application) {
      if(!application.serverGroups) {
        return;
      }

      var executions = application.executions || [];

      application.serverGroups.forEach(function(serverGroup, index) {
        application.serverGroups[index].executions = [];
        executions.forEach(function (execution) {

          var stages = findStagesWithServerGroupInfo(execution.stages);

          _.forEach(stages, function(stage) {
            var stageServerGroup = stage ? extractServerGroupNameFromContext(stage.context) : undefined;
            var stageAccount = stage && stage.context ? stage.context.account || stage.context.credentials : undefined;
            var stageRegion = stage ? extractRegionFromContext(stage.context) : undefined;

            if(_.includes(stageServerGroup, serverGroup.name) &&
              stageAccount === serverGroup.account &&
              stageRegion === serverGroup.region) {
              application.serverGroups[index].executions.push(execution);
            }

          });
        });
      });

      return application;
    }



    function addProvidersAndServerGroupsToInstances(serverGroups) {
      serverGroups.forEach(function(serverGroup) {
        serverGroup.instances.forEach(function(instance) {
          instance.provider = serverGroup.type;
          instance.serverGroup = instance.serverGroup || serverGroup.name;
          instance.vpcId = serverGroup.vpcId;
        });
      });
    }

    function collateServerGroupsIntoClusters(serverGroups) {
      var clusters = [];
      var groupedByAccount = _.groupBy(serverGroups, 'account');
      _.forOwn(groupedByAccount, function(accountServerGroups, account) {
        var groupedByCluster = _.groupBy(accountServerGroups, 'cluster');
        _.forOwn(groupedByCluster, function(clusterServerGroups, clusterName) {
          var cluster = {account: account, name: clusterName, serverGroups: clusterServerGroups};
          addHealthCountsToCluster(cluster);
          clusters.push(cluster);
        });
      });
      addProvidersAndServerGroupsToInstances(serverGroups);
      return clusters;
    }

    function updateLoadBalancers(application) {
      application.serverGroups.forEach(function(serverGroup) {
        serverGroup.loadBalancers = application.loadBalancers.filter(function(loadBalancer) {
          return loadBalancer.serverGroups.some(function(loadBalancerGroup) {
            return loadBalancerGroup.name === serverGroup.name &&
              loadBalancer.region === serverGroup.region &&
              loadBalancer.account === serverGroup.account;
          });
        }).map(function(loadBalancer) { return loadBalancer.name; });
      });
    }

    function normalizeServerGroupsWithLoadBalancers(application) {
      updateLoadBalancers(application);
    }

    function getCluster(application, account, cluster) {
      return Restangular.one('applications', application).one('clusters', account).one(cluster).get();
    }

    function getClusters(application) {
      return Restangular.one('applications', application).one('clusters').get();
    }

    return {
      loadServerGroups: loadServerGroups,
      createServerGroupClusters: collateServerGroupsIntoClusters,
      normalizeServerGroupsWithLoadBalancers: normalizeServerGroupsWithLoadBalancers,
      addTasksToServerGroups: addTasksToServerGroups,
      addExecutionsToServerGroups: addExecutionsToServerGroups,

      //for testing purposes only
      extractRegionFromContext: extractRegionFromContext,
      getCluster: getCluster,
      getClusters: getClusters,
    };

  });
