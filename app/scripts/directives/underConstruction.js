'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('underConstruction', function () {
    return {
      restrict: 'E',
      template: '<div class="text-center"><img src="images/under-construction.gif"/></div>'
    };
  }
);
