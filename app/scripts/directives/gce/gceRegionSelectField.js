'use strict';


angular.module('deckApp')
  .directive('gceRegionSelectField', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/gce/regionSelectField.html',
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
  }
);
