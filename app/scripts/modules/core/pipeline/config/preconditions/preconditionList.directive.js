'use strict';

let angular = require('angular');

require('./preconditionList.directive.html');
require('./modal/editPrecondition.html');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions.preconditionList', [])
    .directive('preconditionList', function () {
      return {
        restrict: 'E',
        scope: {
          preconditions: '=',
          parent: '=',
          strategy: '='
        },
        templateUrl: require('./preconditionList.directive.html'),
        controller: 'PreconditionListCtrl',
        controllerAs: 'preconditionListCtrl'
      };
    })
    .controller('PreconditionListCtrl', function ($scope, $modal, _) {

      var vm = this;

      vm.editPrecondition = function (precondition, strategy) {
        var modalInstance = $modal.open({
          templateUrl: require('./modal/editPrecondition.html'),
          controller: 'EditPreconditionController',
          controllerAs: 'editPrecondition',
          resolve: {
            precondition: function () {
              return precondition;
            },
            strategy: function(){
              return strategy;
            },
          }
        });

        modalInstance.result.then(function (newPrecondition) {
          if (!precondition) {
            $scope.preconditions.push(newPrecondition);
          } else {
            $scope.preconditions[$scope.preconditions.indexOf(precondition)] = newPrecondition;
          }
          vm.isPreconditionsDirty = true;
        });
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
            }
        );
        vm.isPreconditionsDirty = true;
      };

      vm.renderContext = function (precondition) {
        var renderedContext = "";
        _.forEach(precondition.context, function (value, key) {
          renderedContext += "<strong>" + key + ": </strong>" + value + "<br/>";
        });
        return renderedContext;
      };

      return vm;

    });
