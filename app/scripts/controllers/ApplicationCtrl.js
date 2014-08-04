'use strict';

angular.module('deckApp')
  .controller('ApplicationCtrl', function($scope, application) {

    $scope.application = application;

//    oortService.loadClusters($scope.application).then(function() {
//      $scope.loadingClusters = false;
//      $scope.$broadcast('clustersLoaded');
//      console.warn($scope.application);
//    });

//    function getLoadBalancersForAccount(account) {
//      console.warn('getting load balancers for', account);
//      if (!$scope.application.clusters.length) {
//        loadClusters().then(getLoadBalancersForAccount.bind(null, account));
//      } else {
//        var accountClusters = $scope.application.clusters.filter(function (cluster) {
//          return cluster.accountName === account;
//        });
//        return _.flatten(_.collect(accountClusters, 'loadBalancers'));
//      }
//    }
//
//    $scope.getLoadBalancersForAccount = getLoadBalancersForAccount;
//
//    getLoadBalancersForAccount('test');

  });

