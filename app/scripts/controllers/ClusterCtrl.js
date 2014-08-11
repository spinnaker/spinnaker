'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ClusterCtrl', function($scope, cluster, application, oortService, _) {

    $scope.account = cluster.account;
    $scope.cluster = cluster;
    $scope.asgsByRegion = [];

    $scope.sortFilter = {
      allowSorting: false,
      filter: '',
      showAllInstances: true,
      hideHealthy: false
    };

    function filterServerGroups(serverGroups) {
      if (!$scope.sortFilter.hideHealthy) {
        return serverGroups;
      }
      return serverGroups.filter(function(serverGroup) {
        return serverGroup.asg.downCount > 0;
      });
    }

    $scope.updateClusterGroups = function() {
      var groupedAsgs = _.groupBy(cluster.serverGroups, 'region'),
        regions = _.keys(groupedAsgs),
        serverGroupsByRegion = [];

      regions.forEach(function(region) {
        var filtered = filterServerGroups(groupedAsgs[region]);
        if (filtered.length) {
          serverGroupsByRegion.push({ region: region, serverGroups: filtered });
        }
      });
      $scope.serverGroupsByRegion = serverGroupsByRegion;

      $scope.displayOptions = {
        showInstances: $scope.sortFilter.showAllInstances,
        hideHealthy: $scope.sortFilter.hideHealthy
      };
    };

    $scope.cluster = cluster;
    $scope.updateClusterGroups();
  }
);
