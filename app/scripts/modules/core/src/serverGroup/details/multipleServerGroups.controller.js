'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import * as angular from 'angular';

import { PROVIDER_SERVICE_DELEGATE } from '../../cloudProvider/providerService.delegate';
import { ConfirmationModalService } from '../../confirmationModal';
import { CORE_SERVERGROUP_DETAILS_MULTIPLESERVERGROUP_COMPONENT } from './multipleServerGroup.component';
import { SERVER_GROUP_WRITER } from '../serverGroupWriter.service';
import { ClusterState } from '../../state';

export const CORE_SERVERGROUP_DETAILS_MULTIPLESERVERGROUPS_CONTROLLER =
  'spinnaker.core.serverGroup.details.multipleServerGroups.controller';
export const name = CORE_SERVERGROUP_DETAILS_MULTIPLESERVERGROUPS_CONTROLLER; // for backwards compatibility
angular
  .module(CORE_SERVERGROUP_DETAILS_MULTIPLESERVERGROUPS_CONTROLLER, [
    UIROUTER_ANGULARJS,
    SERVER_GROUP_WRITER,
    PROVIDER_SERVICE_DELEGATE,
    CORE_SERVERGROUP_DETAILS_MULTIPLESERVERGROUP_COMPONENT,
  ])
  .controller('MultipleServerGroupsCtrl', [
    '$scope',
    '$state',
    'serverGroupWriter',
    'providerServiceDelegate',
    'app',
    function ($scope, $state, serverGroupWriter, providerServiceDelegate, app) {
      this.serverGroups = [];

      /**
       * Actions
       */

      const getDescriptor = () => {
        let descriptor = this.serverGroups.length + ' server group';
        if (this.serverGroups.length > 1) {
          descriptor += 's';
        }
        return descriptor;
      };

      const confirm = (submitMethodName, verbs) => {
        const descriptor = getDescriptor();
        const monitorInterval = this.serverGroups.length * 1000;
        const taskMonitors = this.serverGroups.map((serverGroup) => {
          const provider = serverGroup.provider || serverGroup.type;
          const providerParamsMixin = providerServiceDelegate.hasDelegate(provider, 'serverGroup.paramsMixin')
            ? providerServiceDelegate.getDelegate(provider, 'serverGroup.paramsMixin')
            : {};

          let mixinParams = {};
          const mixinParamsFactory = providerParamsMixin[submitMethodName];
          if (mixinParamsFactory !== undefined) {
            mixinParams = mixinParamsFactory(serverGroup);
          }

          return {
            application: app,
            title: serverGroup.name,
            submitMethod: (params) =>
              serverGroupWriter[submitMethodName](serverGroup, app, angular.extend(params, mixinParams)),
            monitorInterval: monitorInterval,
          };
        });

        ConfirmationModalService.confirm({
          header: 'Really ' + verbs.simplePresent.toLowerCase() + ' ' + descriptor + '?',
          buttonText: verbs.simplePresent + ' ' + descriptor,
          verificationLabel:
            'Verify the number of server groups (<span class="verification-text">' +
            this.serverGroups.length +
            '</span>) to be ' +
            verbs.futurePerfect.toLowerCase(),
          textToVerify: this.serverGroups.length + '',
          taskMonitorConfigs: taskMonitors,
          askForReason: true,
          multiTaskTitle: verbs.presentContinuous + ' ' + descriptor,
        });
      };

      this.destroyServerGroups = () => {
        confirm('destroyServerGroup', {
          presentContinuous: 'Destroying',
          simplePresent: 'Destroy',
          futurePerfect: 'Destroyed',
        });
      };

      this.disableServerGroups = () => {
        this.serverGroups = this.serverGroups.filter((group) => !group.disabled);
        confirm('disableServerGroup', {
          presentContinuous: 'Disabling',
          simplePresent: 'Disable',
          futurePerfect: 'Disabled',
        });
      };

      this.enableServerGroups = () => {
        this.serverGroups = this.serverGroups.filter((group) => group.disabled);
        confirm('enableServerGroup', {
          presentContinuous: 'Enabling',
          simplePresent: 'Enable',
          futurePerfect: 'Enabled',
        });
      };

      this.canDisable = () => this.serverGroups.some((group) => !group.disabled);

      this.canEnable = () => this.serverGroups.some((group) => group.disabled);

      /***
       * View instantiation/synchronization
       */

      const retrieveServerGroups = () => {
        this.serverGroups = ClusterState.multiselectModel.serverGroups.map((multiselectGroup) => {
          const group = _.cloneDeep(multiselectGroup);
          const match = app.serverGroups.data.find(
            (check) => check.name === group.name && check.account === group.account && check.region === group.region,
          );
          if (match) {
            group.instanceCounts = _.cloneDeep(match.instanceCounts);
            group.disabled = match.isDisabled;
          }
          return group;
        });
      };

      const multiselectWatcher = ClusterState.multiselectModel.serverGroupsStream.subscribe(retrieveServerGroups);
      app.serverGroups.onRefresh($scope, retrieveServerGroups);

      retrieveServerGroups();

      $scope.$on('$destroy', () => {
        if (this.serverGroups.length !== 1) {
          ClusterState.multiselectModel.clearAllServerGroups();
        }
        multiselectWatcher.unsubscribe();
      });
    },
  ]);
