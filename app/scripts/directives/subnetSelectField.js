'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    templateUrl: require('../../views/directives/subnetSelectField.html'),
    scope: {
      subnets: '=',
      component: '=',
      field: '@',
      region: '=',
      onChange: '&',
      labelColumns: '@',
      helpKey: '@',
      readOnly: '=',
    }
  };
};
