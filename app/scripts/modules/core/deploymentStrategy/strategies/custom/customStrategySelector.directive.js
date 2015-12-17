'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.deploymentStrategy.custom.customStrategySelector', [
    ])
    .directive('customStrategySelector', function() {
        return {
            restrict: 'E',
            scope: {
                command: '=',
            },
            templateUrl: require('./customStrategySelector.directive.html'),
            controller: 'CustomStrategySelectorController',
            controllerAs: 'customStrategySelectorController'
        };
    });
