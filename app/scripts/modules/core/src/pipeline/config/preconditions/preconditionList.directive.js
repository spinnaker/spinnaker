'use strict';

const angular = require('angular');

require('./preconditionList.directive.html');
require('./modal/editPrecondition.html');

import './preconditionList.directive.less';

module.exports = angular
  .module('spinnaker.core.pipeline.config.preconditions.preconditionList', [])
  .directive('preconditionList', function() {
    return {
      restrict: 'E',
      scope: {
        preconditions: '=',
        parent: '=',
        strategy: '=',
        application: '=',
      },
      templateUrl: require('./preconditionList.directive.html'),
      controller: 'PreconditionListCtrl',
      controllerAs: 'preconditionListCtrl',
    };
  })
  .controller('PreconditionListCtrl', [
    '$scope',
    '$uibModal',
    function($scope, $uibModal) {
      var vm = this;

      vm.editPrecondition = function(precondition, strategy) {
        var modalInstance = $uibModal.open({
          templateUrl: require('./modal/editPrecondition.html'),
          controller: 'EditPreconditionController',
          controllerAs: 'editPrecondition',
          resolve: {
            precondition: function() {
              return precondition;
            },
            strategy: function() {
              return strategy;
            },
            application: function() {
              return $scope.application;
            },
          },
        });

        modalInstance.result
          .then(function(newPrecondition) {
            if (!precondition) {
              $scope.preconditions.push(newPrecondition);
            } else {
              $scope.preconditions[$scope.preconditions.indexOf(precondition)] = newPrecondition;
            }
            vm.isPreconditionsDirty = true;
          })
          .catch(() => {});
      };

      vm.addPrecondition = function(strategy) {
        if ($scope.parent && !$scope.parent.preconditions) {
          $scope.parent.preconditions = [];
        }
        vm.editPrecondition(undefined, strategy);
      };

      vm.removePrecondition = function(precondition) {
        $scope.preconditions = $scope.preconditions.filter(function(el) {
          return el !== precondition;
        });
        vm.isPreconditionsDirty = true;
      };

      vm.renderContext = function(precondition) {
        var renderedContext = '';
        _.forEach(precondition.context, function(value, key) {
          renderedContext += '<strong>' + key + ': </strong>' + value + '<br/>';
        });
        return renderedContext;
      };

      return vm;
    },
  ]);
