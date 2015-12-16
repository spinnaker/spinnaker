'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.attributes.directive', [
    require('../modal/editApplication.controller.modal.js'),
    require('../../overrideRegistry/override.registry.js'),
  ])
  .directive('applicationAttributes', function (overrideRegistry) {
    return {
      restrict: 'E',
      templateUrl: overrideRegistry.getTemplate('applicationAttributesDirective', require('./applicationAttributes.directive.html')),
      scope: {},
      bindToController: {
        application: '=',
      },
      controller: 'ApplicationAttributesCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ApplicationAttributesCtrl', function ($uibModal, overrideRegistry) {
    this.editApplication = () => {
      $uibModal.open({
        templateUrl: overrideRegistry.getTemplate('editApplicationModal', require('../modal/editApplication.html')),
        controller: 'EditApplicationController',
        controllerAs: 'editApp',
        resolve: {
          application: () => {
            return this.application;
          }
        }
      }).result.then((newAttributes) => {
          this.application.attributes = newAttributes;
        });
    };
  });
