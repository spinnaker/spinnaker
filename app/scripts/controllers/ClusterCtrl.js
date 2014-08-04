'use strict';

angular.module('deckApp')
  .controller('ClusterCtrl', function($scope, account, cluster, application, oortService, _) {

    $scope.account = account.name;
    $scope.cluster = cluster;
    $scope.asgsByRegion = [];

    oortService.getCluster(application.name, account.name, cluster.name).then(function(cluster) {
      var groupedAsgs = _.groupBy(cluster.serverGroups, 'region'),
          regions = _.keys(groupedAsgs),
          asgsByRegion = [];

      regions.forEach(function(region) {
        asgsByRegion.push({ region: region, serverGroups: groupedAsgs[region] });
      });

      $scope.cluster = cluster;
      $scope.asgsByRegion = asgsByRegion;
    });

  })
;
