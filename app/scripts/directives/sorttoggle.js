'use strict';

require('../app');
var angular = require('angular');

/**
 * @ngdoc directive
 * @name scumApp.directive:sortToggle
 * @description
 * # sortToggle
 */

angular.module('deckApp')
  .directive('sortToggle', function () {
    return {
      templateUrl: 'views/sorttoggle.html',
      scope: {
        key: '@',
        label: '@',
        'default': '@',
        onChange: '&',
      },
      restrict: 'A',
      controller: 'SortToggleCtrl as ctrl'
    };
  }
);
