'use strict';

let angular = require('angular');

require('./cloudProviderLogo.less');

module.exports = angular
  .module('spinnaker.core.cloudProviderLogo.directive', [
  ])
  .directive('cloudProviderLogo', function () {
    return {
      restrict: 'E',
      template: '<span class="icon icon-{{provider}} icon-{{state}}" style="height: {{height}}; width: {{width}}"></span>',
      scope: {
        provider: '=',
        state: '@',
        height: '@',
        width: '@',
      },
      link: function(scope, elem) {
        elem.height(scope.height).width(scope.width);
      },
    };
  });
