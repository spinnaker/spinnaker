'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.parameters.parameter', [])
  .directive('parameter', function() {
    return {
      restrict: 'E',
      scope: {
        parameter: '=',
        pipeline: '=',
      },
      controller: 'ParameterCtrl as parameterCtrl',
      templateUrl: require('./parameter.html'),
    };
  })
  .controller('ParameterCtrl', ['$scope', function($scope) {
    this.remove = function(parameter) {
      var index = $scope.pipeline.parameterConfig.indexOf(parameter);
      $scope.pipeline.parameterConfig.splice(index, 1);
    };

    this.addOption = function(parameter) {
      parameter.options.push({ value: '' });
    };

    this.setupOptions = function(parameter) {
      if (!parameter.options) {
        parameter.options = [];
        this.addOption(parameter);
      }
    };

    this.removeOption = function(index, parameter) {
      parameter.options.splice(index, 1);
    };
  }]);
