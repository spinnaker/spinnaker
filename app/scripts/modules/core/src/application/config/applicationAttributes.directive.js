'use strict';

import { get } from 'lodash';

const angular = require('angular');

import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';

module.exports = angular
  .module('spinnaker.core.application.config.attributes.directive', [
    require('../modal/editApplication.controller.modal').name,
    OVERRIDE_REGISTRY,
  ])
  .directive('applicationAttributes', function(overrideRegistry) {
    return {
      restrict: 'E',
      templateUrl: overrideRegistry.getTemplate(
        'applicationAttributesDirective',
        require('./applicationAttributes.directive.html'),
      ),
      scope: {},
      bindToController: {
        application: '=',
      },
      controller: 'ApplicationAttributesCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ApplicationAttributesCtrl', function($uibModal, overrideRegistry) {
    const cpHealthMsg = 'considers only cloud provider health when executing tasks';
    const healthOverrideMsg = 'shows a health override option for each operation';
    const setHealthMessage = () => {
      const hasHealth = get(this.application, 'attributes.platformHealthOnly', false);
      const hasOverride = get(this.application, 'attributes.platformHealthOnlyShowOverride', false);
      this.healthMessage = 'This application ';
      if (hasHealth) {
        this.healthMessage += cpHealthMsg;
        if (hasOverride) {
          this.healthMessage += `. and ${healthOverrideMsg}.`;
        } else {
          this.healthMessage += '.';
        }
      } else if (hasOverride) {
        this.healthMessage += `${healthOverrideMsg}.`;
      }
    };
    setHealthMessage();

    const setPermissions = () => {
      const permissions = get(this.application, 'attributes.permissions');
      if (permissions) {
        const permissionsMap = new Map();
        permissions.READ.forEach(role => {
          permissionsMap.set(role, 'read');
        });
        permissions.WRITE.forEach(role => {
          if (permissionsMap.has(role)) {
            permissionsMap.set(role, permissionsMap.get(role) + ', write');
          } else {
            permissionsMap.set(role, 'write');
          }
        });

        if (permissionsMap.size) {
          this.permissions = Array.from(permissionsMap)
            .map(([role, accessTypes]) => `${role} (${accessTypes})`)
            .join(', ');
        } else {
          this.permissions = null;
        }
      } else {
        this.permissions = null;
      }
    };
    setPermissions();

    this.editApplication = () => {
      $uibModal
        .open({
          templateUrl: overrideRegistry.getTemplate('editApplicationModal', require('../modal/editApplication.html')),
          controller: overrideRegistry.getController('EditApplicationController'),
          controllerAs: 'editApp',
          resolve: {
            application: () => {
              return this.application;
            },
          },
        })
        .result.then(newAttributes => {
          this.application.attributes = newAttributes;
          setHealthMessage();
          setPermissions();
        })
        .catch(() => {});
    };
  });
