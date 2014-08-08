'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('loadBalancersTag', function () {
    return {
      restrict: 'E',
      templateUrl: 'views/application/loadBalancer/loadBalancersTag.html',
      scope: {
        loadBalancers: '=',
        maxDisplay: '='
      },
      link: function(scope) {
        scope.popover = { show: false };
      }
    };
  }
);
