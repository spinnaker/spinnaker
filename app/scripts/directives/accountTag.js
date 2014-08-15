'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('accountTag', function () {
    return {
      restrict: 'E',
      template: '<span class="label label-default account-label account-label-{{account}} {{pad}}">{{account}}</span>',
      scope: {
        account: '=',
        pad: '@?'
      }
    };
  }
);
