'use strict';

module.exports = function($scope, $rootScope, loadBalancer, application) {

  $scope.loadBalancer = application.loadBalancers.filter(function(test) {
    return test.name === loadBalancer.name && test.region === loadBalancer.region && test.account === loadBalancer.accountId;
  })[0];

};
