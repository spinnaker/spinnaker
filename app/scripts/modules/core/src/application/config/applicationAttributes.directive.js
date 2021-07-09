'use strict';

import { module } from 'angular';
import { get } from 'lodash';

import { SETTINGS } from '../../config/settings';
import { CORE_APPLICATION_MODAL_EDITAPPLICATION_CONTROLLER_MODAL } from '../modal/editApplication.controller.modal';
import { OVERRIDE_REGISTRY } from '../../overrideRegistry/override.registry';

export const CORE_APPLICATION_CONFIG_APPLICATIONATTRIBUTES_DIRECTIVE =
  'spinnaker.core.application.config.attributes.directive';
export const name = CORE_APPLICATION_CONFIG_APPLICATIONATTRIBUTES_DIRECTIVE; // for backwards compatibility
module(CORE_APPLICATION_CONFIG_APPLICATIONATTRIBUTES_DIRECTIVE, [
  CORE_APPLICATION_MODAL_EDITAPPLICATION_CONTROLLER_MODAL,
  OVERRIDE_REGISTRY,
])
  .directive('applicationAttributes', [
    'overrideRegistry',
    function (overrideRegistry) {
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
    },
  ])
  .controller('ApplicationAttributesCtrl', [
    '$uibModal',
    'overrideRegistry',
    function ($uibModal, overrideRegistry) {
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
          (permissions.READ || []).forEach((role) => {
            permissionsMap.set(role, 'read');
          });
          (permissions.EXECUTE || []).forEach((role) => {
            if (permissionsMap.has(role)) {
              permissionsMap.set(role, permissionsMap.get(role) + ', execute');
            } else {
              permissionsMap.set(role, 'execute');
            }
          });
          (permissions.WRITE || []).forEach((role) => {
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
          .result.then((newAttributes) => {
            this.application.attributes = newAttributes;
            setHealthMessage();
            setPermissions();
          })
          .catch(() => {});
      };

      this.slackBaseUrl = get(SETTINGS, 'slack.baseUrl', '');
    },
  ]);
