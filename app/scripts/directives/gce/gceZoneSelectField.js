'use strict';


angular.module('spinnaker')
  .directive('gceZoneSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/gce/zoneSelectField.html',
      scope: {
        zones: '=',
        component: '=',
        field: '@',
        account: '=',
        onChange: '&',
        labelColumns: '@'
      }
    };
  }
);
