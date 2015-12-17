'use strict';

const angular = require('angular');

require('./buttonBusyIndicator.directive.less');

module.exports = angular
  .module('spinnaker.core.forms.buttonBusyIndicator.directive', [])
  .directive('buttonBusyIndicator', function () {
    return {
      restrict: 'E',
      templateUrl: require('./buttonBusyIndicator.directive.html')
    };
  });
