'use strict';


angular.module('deckApp')
  .directive('loadBalancer', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/application/loadBalancer/loadBalancer.html',
      controller: 'LoadBalancerDirectiveCtrl as ctrl',
      scope: {
        loadBalancer: '=',
        displayOptions: '='
      }
    };
  })
  .controller('LoadBalancerDirectiveCtrl', function($scope, $rootScope) {

    $scope.$state = $rootScope.$state;

    this.displayServerGroup = function (serverGroup) {
      if ($scope.displayOptions.hideHealthy) {
        return serverGroup.downCount > 0;
      }
      return $scope.displayOptions.showServerGroups;
    };

  });
