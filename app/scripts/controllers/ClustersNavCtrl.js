'use strict';

module.exports = function($scope, application) {

  $scope.application = application;

  $scope.getClustersForAccount = function(account) {
    return $scope.clusters.filter(function(cluster) {
      return cluster.accountName === account;
    });
  };

  $scope.clusters = application.clusters;
  $scope.clustersLoaded = true;
};

