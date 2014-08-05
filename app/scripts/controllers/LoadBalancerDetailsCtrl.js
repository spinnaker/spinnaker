'use strict';

angular.module('deckApp')
  .controller('LoadBalancerDetailsCtrl', function($scope, $rootScope, loadBalancer, application) {

    function extractLoadBalancer(loadBalancers) {
      $scope.loadBalancer = loadBalancers.filter(function(test) {
        return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId;
      })[0];
    }

    application.getLoadBalancers().then(extractLoadBalancer.bind(null));

  })
;
