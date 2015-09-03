'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.instance.instanceList.directive', [
  require('../clusterFilter/clusterFilterModel.js'),
  require('./instanceListBody.directive.js'),
])
  .directive('instanceList', function (ClusterFilterModel) {
    return {
      restrict: 'E',
      templateUrl: require('./instanceList.directive.html'),
      scope: {
        instances: '=',
        sortFilter: '=',
      },
      link: function (scope) {
        scope.applyParamsToUrl = ClusterFilterModel.applyParamsToUrl;
      }
    };
  }).name;
