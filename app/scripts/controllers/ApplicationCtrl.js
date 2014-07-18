'use strict';

angular.module('deckApp')
  .controller('ApplicationCtrl', function($scope, application, applicationService) {

    $scope.view = {
      activeTab: 'clusters'
    };

    applicationService.getApplication(application.name).then(function(response) {
      var data = response.data;
      $scope.application = data;
      $scope.application.clusters = [];
      $scope.application.accounts = [];

      applicationService.getClusters(application.name).then(function(response) {
        var clustersByAccount = response.data;
        var accounts = Object.keys(clustersByAccount);
        $scope.application.accounts = accounts;
        accounts.forEach(function(account) {
          var clusters = clustersByAccount[account];
          clusters.forEach(function(clusterName) {
            applicationService.getCluster(application.name, account, clusterName).then(function(response) {
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

  })
  .factory('applicationService', function($http) {

    function getApplication(application) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application);
    }

    function getClusters(application) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application + '/clusters');
    }

    function getClustersForAccount(application, account) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application + '/clusters/' + account);
    }

    function getCluster(application, account, cluster) {
      return $http.get('http://oort.prod.netflix.net/applications/' + application + '/clusters/' + account + '/' + cluster);
    }

    return {
      getApplication: getApplication,
      getClusters: getClusters,
      getClustersForAccount: getClustersForAccount,
      getCluster: getCluster
    };
  });
