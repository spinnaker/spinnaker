'use strict';


angular.module('deckApp')
  .directive('zoneSelectField', function () {
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
