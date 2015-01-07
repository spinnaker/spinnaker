'use strict';


angular.module('deckApp')
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
        instance.hasHealthStatus = _.some(healthList, function(health) {
          return _.contains(['LoadBalancer', 'Discovery'], health.type);
        });
      });
    }

    function addHealthyCountsToServerGroup(serverGroup) {
      addHealthStatusCheck(serverGroup);
      serverGroup.upCount = _.filter(serverGroup.instances, {isHealthy: true}).length;
      serverGroup.downCount = _.filter(serverGroup.instances, {isHealthy: false}).length;
      serverGroup.missingHealthCount = _.filter(serverGroup.instances, {hasHealthStatus: false}).length;
      serverGroup.unknownCount = 0;
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
      normalizeServerGroupsWithLoadBalancers: normalizeServerGroupsWithLoadBalancers
    };

  });
