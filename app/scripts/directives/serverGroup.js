'use strict';

module.exports = function() {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: 'views/application/cluster/serverGroup.html',
    scope: {
      cluster: '=',
      serverGroup: '=',
      lazyRenderInstances: '='
    },
    link: function(scope) {
      scope.instanceDisplay = {
        lazyRenderInstances: scope.lazyRenderInstances,
        displayed: !scope.lazyRenderInstances || scope.serverGroup.asg.instances.length < 20
      };
      scope.$state = scope.$parent.$state;
    }
  };
};
