'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('setFocus', function() {
    return {
      restrict: 'A',
      controller: function($scope, $element) {
        $element[0].focus();
      },
    };
  });
