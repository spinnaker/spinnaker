'use strict';

module.exports = function() {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: 'views/application/loadBalancer/loadBalancerServerGroup.html',
    scope: {
      serverGroup: '=',
      asgFilter: '='
    },
    link: function(scope) {
      scope.instanceDisplay = {
        displayed: false
      };
      scope.$state = scope.$parent.$state;
      scope.sortFilter= scope.$parent.sortFilter;
    }
  };
};
