'use strict';

require('../../app');
var angular = require('angular');

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
        onChange: '&'
      }
    };
  }
);
