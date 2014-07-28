'use strict';

angular.module('deckApp')
  .controller('ClusterCtrl', function($scope, account, cluster, application, oortService, _) {

    $scope.account = account.name;
    $scope.cluster = cluster;
    $scope.asgsByRegion = [];

    oortService.getCluster(application.data.name, account.name, cluster.name).then(function(response) {
      var cluster = response.data[0],
          groupedAsgs = _.groupBy(cluster.serverGroups, 'region'),
          regions = _.keys(groupedAsgs),
          asgsByRegion = [];

      regions.forEach(function(region) {
        asgsByRegion.push({ region: region, serverGroups: groupedAsgs[region] });
      });

      // add upCount
      // TODO: move to Restangular, result transformer
      cluster.serverGroups.forEach(function(serverGroup) {
        serverGroup.upCount = _.filter(serverGroup.instances, {isHealthy: true}).length;
      });
      $scope.cluster = cluster;
      $scope.asgsByRegion = asgsByRegion;
    });

  })
;
