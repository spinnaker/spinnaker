'use strict';

angular.module('deckApp')
  .controller('ApplicationCtrl', function($scope, application, oortService) {

    $scope.view = {
      activeTab: 'clusters'
    };

    oortService.getApplication(application.name).then(function(response) {
      $scope.application = response.data;
      $scope.application.clusters = [];
      $scope.application.accounts = [];

      oortService.getClusters(application.name).then(function(response) {
        var clustersByAccount = response.data;
        var accounts = Object.keys(clustersByAccount);
        $scope.application.accounts = accounts;
        accounts.forEach(function(account) {
          var clusters = clustersByAccount[account];
          clusters.forEach(function(clusterName) {
            oortService.getCluster(application.name, account, clusterName).then(function(response) {
              $scope.application.clusters.push(response.data[0]);
            });
          });
        });
      });

    });

    $scope.getClustersForAccount = function(account) {
      return $scope.application.clusters.filter(function(cluster) {
        return cluster.accountName === account;
      });
    };

    $scope.listClusters = function() {

    };

  });

