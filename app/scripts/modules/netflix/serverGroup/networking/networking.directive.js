'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.serverGroup.networking.directive', [
    require('./networking.controller.js'),
  ])
  .directive('nflxServerGroupNetworking', function () {
    return {
      restrict: 'E',
      templateUrl: require('./networking.directive.html'),
      bindToController: {
        serverGroup: '=',
        application: '=',
      },
      controller: 'networkingCtrl',
      controllerAs: 'vm',
      scope: {},
    };
  });
