'use strict';

let angular = require('angular');

import { UPDATE_FAST_PROPERTY_WIZARD_CONTROLLER } from '../wizard/updateFastPropertyWizard.controller';
import { DELETE_FAST_PROPERTY_WIZARD_CONTROLLER } from '../wizard/deleteFastPropertyWizard.controller';

module.exports = angular
  .module('spinnaker.netflix.globalFastProperties.details.controller', [
    require('angular-ui-router'),
    require('../fastProperty.read.service'),
    require('../fastProperty.write.service'),
    UPDATE_FAST_PROPERTY_WIZARD_CONTROLLER,
    DELETE_FAST_PROPERTY_WIZARD_CONTROLLER
  ])
  .controller('GlobalFastPropertiesDetailsController', function($scope, $state, $uibModal, app, fastProperty, fastPropertyReader) {

    let vm = this;

    let extractEnvFromId = (propertyId) => {
      let list = propertyId.split('|');
      return list[2] || 'prod';
    };

    let getProperty = (environment) => {
      let env = environment || extractEnvFromId(fastProperty.propertyId);
      fastPropertyReader.getPropByIdAndEnv(fastProperty.propertyId, env)
        .then((results) => {
          vm.property = results.property;
        })
        .catch(() => {
            let otherEnv = env === 'prod' ? 'test' : 'prod';
            getProperty(otherEnv);
        });
    };

    vm.editFastProperty = function(property) {
      $uibModal.open({
        templateUrl: require('./../wizard/updateFastPropertyWizard.html'),
        controller:  'updateFastPropertyWizardController',
        controllerAs: 'ctrl',
        size: 'lg',
        resolve: {
          title: () => 'Update Fast Property',
          property: property,
          applicationName: () => app ? app.applicationName : 'spinnakerfp'
        }
      });
    };

    vm.delete = function(property) {
      $uibModal.open({
        templateUrl: require('./../wizard/deleteFastPropertyWizard.html'),
        controller:  'deleteFastPropertyWizardController',
        controllerAs: 'ctrl',
        size: 'lg',
        resolve: {
          title: () => 'Delete Fast Property',
          property: property,
          applicationName: () => app ? app.applicationName : 'spinnakerfp'
        }
      });
    };


    getProperty();
  });
