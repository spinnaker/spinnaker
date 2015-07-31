'use strict';

require('../../../views/directives/gce/regionSelectField.html');

module.exports = function () {
  return {
    restrict: 'E',
    templateUrl: require('../../../views/directives/gce/regionSelectField.html'),
    scope: {
      regions: '=',
      component: '=',
      field: '@',
      account: '=',
      onChange: '&',
      labelColumns: '@',
      readOnly: '=',
    }
  };
};
