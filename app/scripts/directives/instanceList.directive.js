'use strict';

module.exports = function(ClusterFilterModel) {
  return {
    restrict: 'E',
    templateUrl: require('./instanceList.directive.html'),
    scope: {
      instances: '=',
      sortFilter: '=',
    },
    link: function(scope) {
      scope.applyParamsToUrl = ClusterFilterModel.applyParamsToUrl;
    }
  };
};
