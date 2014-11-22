'use strict';


angular.module('deckApp')
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
