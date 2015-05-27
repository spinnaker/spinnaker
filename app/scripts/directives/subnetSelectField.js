'use strict';


angular.module('spinnaker')
  .directive('subnetSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/subnetSelectField.html',
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
  }
);
