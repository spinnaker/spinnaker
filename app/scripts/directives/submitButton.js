'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('submitButton', function () {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/modal/submitButton.html',
      scope: {
        onClick: '&',
        isDisabled: '=',
        isNew: '=',
        submitting: '='
      }
    };
  }
);
