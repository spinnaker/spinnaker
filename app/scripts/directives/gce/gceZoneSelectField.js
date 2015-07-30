'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    templateUrl: require('../../../views/directives/gce/zoneSelectField.html'),
    scope: {
      zones: '=',
      component: '=',
      field: '@',
      account: '=',
      onChange: '&',
      labelColumns: '@'
    }
  };
};
