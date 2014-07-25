'use strict';

angular.module('deckApp')
  .controller('ApplicationCtrl', function($scope, application, oortService, $q) {

      $scope.application = application.data;
      $scope.application.clusters = [];
      $scope.application.accounts = [];

      oortService.getClusters($scope.application.name).then(function(response) {
        var clustersByAccount = response.data;
        var accounts = Object.keys(clustersByAccount);
        $scope.application.accounts = accounts;

        var clusterFetches = [];
        accounts.forEach(function(account) {
          var clusters = clustersByAccount[account];
          clusters.forEach(function(clusterName) {
            var fetch = oortService.getCluster($scope.application.name, account, clusterName);
            clusterFetches.push(fetch);
            fetch.then(function(response) {
              $scope.application.clusters.push(response.data[0]);
            });
          });
        });

        $q.all(clusterFetches).then(function() {
          $scope.loadingClusters = false;
        });
      });

    $scope.loadingClusters = true;

    $scope.getClustersForAccount = function(account) {
      return $scope.application.clusters.filter(function(cluster) {
        return cluster.accountName === account;
      });
    };

    $scope.listClusters = function() {

    };

  });

