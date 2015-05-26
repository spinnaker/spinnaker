'use strict';


angular.module('spinnaker.cluster.service', [
  'restangular'
])
  .factory('clusterService', function ($q, Restangular) {

    function loadServerGroups(applicationName) {
      return Restangular.one('applications', applicationName).one('serverGroups').getList()
        .then(function (serverGroups) {
          serverGroups.forEach(addHealthyCountsToServerGroup);
          return serverGroups;
        });
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

    function addHealthyCountsToServerGroup(serverGroup) {
      addHealthStatusCheck(serverGroup);
      serverGroup.totalCount = serverGroup.instanceCounts.total;
      serverGroup.upCount = serverGroup.instanceCounts.up;
      serverGroup.downCount = serverGroup.instanceCounts.down;
      serverGroup.unknownCount = serverGroup.instanceCounts.unknown + serverGroup.instanceCounts.starting;
      serverGroup.startingCount = serverGroup.instanceCounts.starting;
      serverGroup.outOfServiceCount = serverGroup.instanceCounts.outOfService;
    }

    function addHealthCountsToCluster(cluster) {
      cluster.totalCount = 0;
      cluster.upCount = 0;
      cluster.downCount = 0;
      cluster.unknownCount = 0;
      cluster.startingCount = 0;
      cluster.outOfServiceCount = 0;
      if (!cluster.serverGroups) {
        return;
      }
      cluster.serverGroups.forEach(function(serverGroup) {
        if (serverGroup.isDisabled) {
          return;
        }
        cluster.totalCount += serverGroup.totalCount || 0;
        cluster.upCount += serverGroup.upCount || 0;
        cluster.downCount += serverGroup.downCount || 0;
        cluster.unknownCount += serverGroup.unknownCount || 0;
        cluster.startingCount += serverGroup.startingCount || 0;
        cluster.outOfServiceCount += serverGroup.outOfServiceCount || 0;
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
        return _.intersection(_.pluck(serverGroup.instances, 'id'), task.getValueFor('instanceIds')).length > 0;
      }
      return false;
    }

    var taskMatchers = {
      'createcopylastasg': function(task, serverGroup) {
        var source = task.getValueFor('source'),
            targetAccount = task.getValueFor('deploy.account.name'),
            targetRegion = task.getValueFor('availabilityZones') ? Object.keys(task.getValueFor('availabilityZones'))[0] : null,
            targetServerGroup = targetRegion && task.getValueFor('deploy.server.groups') ? task.getValueFor('deploy.server.groups')[targetRegion][0] : null,
            sourceServerGroup = source.asgName,
            sourceAccount = source.account,
            sourceRegion = source.region;

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
      'registerinstanceswithloadbalancer': instanceIdsTaskMatcher,
      'deregisterinstancesfromloadbalancer': instanceIdsTaskMatcher,
      'terminateinstances': instanceIdsTaskMatcher,
      'rebootinstances': instanceIdsTaskMatcher,
      'resizeasg': baseTaskMatcher,
      'disableasg': baseTaskMatcher,
      'destroyasg': baseTaskMatcher,
      'enableasg': baseTaskMatcher,
      'destroygooglereplicapool': baseTaskMatcher,
      'enablegoogleservergroup': baseTaskMatcher,
      'disablegoogleservergroup': baseTaskMatcher,
      'resizegooglereplicapool': baseTaskMatcher
    };

    function taskMatches(task, serverGroup) {
      var matcher = taskMatchers[task.getValueFor('notification.type')];
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
         return ( _.includes(['deploy', 'destroyAsg', 'resizeAsg'], stage.type)  &&  _.has(stage.context, 'deploy.server.groups') ) ||
           ( stage.type === 'disableAsg'  && _.has(stage.context, 'targetop.asg.disableAsg.name') );
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

      application.serverGroups.forEach(function(serverGroup, index){
        application.serverGroups[index].executions = [];
        executions.forEach(function (execution) {

          var stages = findStagesWithServerGroupInfo(execution.stages);

          _.forEach(stages, function(stage) {
            var stageServerGroup = stage ? extractServerGroupNameFromContext(stage.context): undefined;
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

    return {
      loadServerGroups: loadServerGroups,
      createServerGroupClusters: collateServerGroupsIntoClusters,
      normalizeServerGroupsWithLoadBalancers: normalizeServerGroupsWithLoadBalancers,
      addTasksToServerGroups: addTasksToServerGroups,
      addExecutionsToServerGroups: addExecutionsToServerGroups,

      //for testing purposes only
      extractRegionFromContext: extractRegionFromContext,
    };

  });
