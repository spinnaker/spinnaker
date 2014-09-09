'use strict';


angular.module('deckApp')
  .directive('availabilityZoneSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/availabilityZoneSelectField.html',
      scope: {
        availabilityZones: '=',
        component: '=',
        field: '@',
        region: '=',
        onChange: '&'
      }
    };
  }
);
