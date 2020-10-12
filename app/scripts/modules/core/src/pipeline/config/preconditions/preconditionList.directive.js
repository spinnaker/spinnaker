'use strict';

import { module } from 'angular';

require('./preconditionList.directive.html');
require('./modal/editPrecondition.html');

import './preconditionList.directive.less';

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONLIST_DIRECTIVE =
  'spinnaker.core.pipeline.config.preconditions.preconditionList';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONLIST_DIRECTIVE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONLIST_DIRECTIVE, [])
  .directive('preconditionList', function () {
    return {
      restrict: 'E',
      scope: {
        preconditions: '=',
        parent: '=',
        strategy: '=',
        application: '=',
        upstreamStages: '=',
      },
      templateUrl: require('./preconditionList.directive.html'),
      controller: 'PreconditionListCtrl',
      controllerAs: 'preconditionListCtrl',
    };
  })
  .controller('PreconditionListCtrl', [
    '$scope',
    '$uibModal',
    function ($scope, $uibModal) {
      const vm = this;

      vm.editPrecondition = function (precondition, strategy) {
        const modalInstance = $uibModal.open({
          templateUrl: require('./modal/editPrecondition.html'),
          controller: 'EditPreconditionController',
          controllerAs: 'editPrecondition',
          resolve: {
            precondition: function () {
              return precondition;
            },
            strategy: function () {
              return strategy;
            },
            application: function () {
              return $scope.application;
            },
            upstreamStages: function () {
              return $scope.upstreamStages;
            },
          },
        });

        modalInstance.result
          .then(function (newPrecondition) {
            if (!precondition) {
              $scope.preconditions.push(newPrecondition);
            } else {
              $scope.preconditions[$scope.preconditions.indexOf(precondition)] = newPrecondition;
            }
            vm.isPreconditionsDirty = true;
          })
          .catch(() => {});
      };

      vm.addPrecondition = function (strategy) {
        if ($scope.parent && !$scope.parent.preconditions) {
          $scope.parent.preconditions = [];
        }
        vm.editPrecondition(undefined, strategy);
      };

      vm.removePrecondition = function (precondition) {
        $scope.preconditions = $scope.preconditions.filter(function (el) {
          return el !== precondition;
        });
        vm.isPreconditionsDirty = true;
      };

      vm.renderContext = function (precondition) {
        let renderedContext = '';
        _.forEach(precondition.context, function (value, key) {
          renderedContext += '<strong>' + key + ': </strong>' + value + '<br/>';
        });
        return renderedContext;
      };

      return vm;
    },
  ]);
