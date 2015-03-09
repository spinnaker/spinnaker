'use strict';


angular.module('deckApp.cluster.service', [
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
      serverGroup.unknownCount = serverGroup.instanceCounts.unknown;
      serverGroup.startingCount = serverGroup.instanceCounts.starting;
      serverGroup.outOfServiceCount = serverGroup.instanceCounts.outOfService;
    }

    function addHealthCountsToCluster(cluster) {
      cluster.upCount = 0;
      cluster.downCount = 0;
      cluster.unknownCount = 0;
      if (!cluster.serverGroups) {
        return;
      }
      cluster.serverGroups.forEach(function(serverGroup) {
        if (serverGroup.isDisabled) {
          return;
        }
        cluster.upCount += serverGroup.upCount;
        cluster.downCount += serverGroup.downCount;
        cluster.unknownCount += serverGroup.unknownCount;
        cluster.missingHealthCount += serverGroup.missingHealthCount;
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
            serverGroupName = serverGroup ? task.getValueFor('deploy.server.groups')[region][0] : null;

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

    function addProvidersToInstances(serverGroups) {
      serverGroups.forEach(function(serverGroup) {
        serverGroup.instances.forEach(function(instance) {
          instance.provider = serverGroup.type;
        });
      });
    }

    function setInstancesDisabled(serverGroups) {
      _.filter(serverGroups, 'isDisabled').forEach(function(serverGroup) {
        serverGroup.instances.forEach(function(instance) {
          instance.healthStatus = 'Disabled';
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
      addProvidersToInstances(serverGroups);
      setInstancesDisabled(serverGroups);
      return clusters;
    }

    function updateLoadBalancers(application) {
      application.serverGroups.forEach(function(serverGroup) {
        serverGroup.loadBalancers = application.loadBalancers.filter(function(loadBalancer) {
          return loadBalancer.serverGroups.indexOf(serverGroup) !== -1;
        });
      });
    }

    function normalizeServerGroupsWithLoadBalancers(application) {
      updateLoadBalancers(application);
    }

    return {
      loadServerGroups: loadServerGroups,
      createServerGroupClusters: collateServerGroupsIntoClusters,
      normalizeServerGroupsWithLoadBalancers: normalizeServerGroupsWithLoadBalancers,
      addTasksToServerGroups: addTasksToServerGroups
    };

  });
