'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.instance.details.console.link', [
    require('angular-ui-bootstrap'),
    require('./consoleOutput.modal.controller.js'),
  ])
  .directive('consoleOutputLink', function () {
    return {
      restrict: 'E',
      template: '<a href ng-click="vm.showConsoleOutput()">{{vm.text}}</a>',
      scope: {},
      bindToController: {
        instance: '=',
        text: '='
      },
      controllerAs: 'vm',
      controller: function($uibModal) {
        this.text = this.text || 'Console Output (Raw)';
        this.showConsoleOutput = function  () {
          $uibModal.open({
            templateUrl: require('./consoleOutput.modal.html'),
            controller: 'ConsoleOutputCtrl as ctrl',
            size: 'lg',
            resolve: {
              instance: () => this.instance
            }
          });
        };
      },
    };
  });
