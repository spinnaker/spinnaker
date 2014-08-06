'use strict';

angular.module('deckApp')
  .controller('ClusterCtrl', function($scope, cluster, application, oortService, _) {

    $scope.account = cluster.account;
    $scope.cluster = cluster;
    $scope.asgsByRegion = [];

    function groupAsgsByRegion() {
      var groupedAsgs = _.groupBy(cluster.serverGroups, 'region'),
        regions = _.keys(groupedAsgs),
        asgsByRegion = [];

      regions.forEach(function(region) {
        asgsByRegion.push({ region: region, serverGroups: groupedAsgs[region] });
      });
      $scope.asgsByRegion = asgsByRegion;
    }

    $scope.cluster = cluster;

    groupAsgsByRegion();

  })
;
