'use strict';

require('../modules/instance/instanceList.html');

module.exports = function(clusterFilterService) {
  return {
    restrict: 'E',
    templateUrl: require('../modules/instance/instanceList.html'),
    scope: {
      instances: '=',
      sortFilter: '=',
    },
    link: function(scope) {
      scope.updateQueryParams = clusterFilterService.updateQueryParams;
    }
  };
};
