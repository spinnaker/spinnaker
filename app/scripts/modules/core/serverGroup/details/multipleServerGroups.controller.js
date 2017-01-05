'use strict';

import {CONFIRMATION_MODAL_SERVICE} from 'core/confirmationModal/confirmationModal.service';
import {SERVER_GROUP_WRITER_SERVICE} from 'core/serverGroup/serverGroupWriter.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.details.multipleServerGroups.controller', [
    require('angular-ui-router'),
    SERVER_GROUP_WRITER_SERVICE,
    CONFIRMATION_MODAL_SERVICE,
    require('../../insight/insightFilterState.model'),
    require('../../cluster/filter/multiselect.model'),
    require('../../cloudProvider/serviceDelegate.service.js'),
    require('./multipleServerGroup.component'),
  ])
  .controller('MultipleServerGroupsCtrl', function ($scope, $state, InsightFilterStateModel,
                                                    confirmationModalService, MultiselectModel,
                                                    serverGroupWriter, serviceDelegate, app) {

      this.InsightFilterStateModel = InsightFilterStateModel;
      this.serverGroups = [];

      /**
       * Actions
       */

      let getDescriptor = () => {
        let descriptor = this.serverGroups.length + ' server group';
        if (this.serverGroups.length > 1) {
          descriptor += 's';
        }
        return descriptor;
      };

      let confirm = (submitMethodName, verbs) => {
        let descriptor = getDescriptor(),
            monitorInterval = this.serverGroups.length * 1000;
        let taskMonitors = this.serverGroups.map(serverGroup => {
          let provider = serverGroup.provider || serverGroup.type;
          let providerParamsMixin = serviceDelegate.hasDelegate(provider, 'serverGroup.paramsMixin') ?
            serviceDelegate.getDelegate(provider, 'serverGroup.paramsMixin') :
            {};

          let mixinParams = {};
          let mixinParamsFactory = providerParamsMixin[submitMethodName];
          if (mixinParamsFactory !== undefined) {
            mixinParams = mixinParamsFactory(serverGroup);
          }

          return {
            application    : app,
            title          : serverGroup.name,
            submitMethod   : (params) => serverGroupWriter[submitMethodName](serverGroup, app, angular.extend(params, mixinParams)),
            monitorInterval: monitorInterval,
          };
        });

        confirmationModalService.confirm({
          header           : 'Really ' + verbs.simplePresent.toLowerCase() + ' ' + descriptor + '?',
          buttonText       : verbs.simplePresent + ' ' + descriptor,
          verificationLabel: 'Verify the number of server groups (<span class="verification-text">' + this.serverGroups.length + '</span>) to be ' + verbs.futurePerfect.toLowerCase(),
          textToVerify     : this.serverGroups.length + '',
          taskMonitors     : taskMonitors,
          askForReason     : true,
          multiTaskTitle   : verbs.presentContinuous + ' ' + descriptor,
        });
      };

      this.destroyServerGroups = () => {
        confirm('destroyServerGroup', {
          presentContinuous: 'Destroying',
          simplePresent    : 'Destroy',
          futurePerfect    : 'Destroyed'
        });
      };

      this.disableServerGroups = () => {
        confirm('disableServerGroup', {
          presentContinuous: 'Disabling',
          simplePresent    : 'Disable',
          futurePerfect    : 'Disabled'
        });
      };

      this.enableServerGroups = () => {
        confirm('enableServerGroup', {
          presentContinuous: 'Enabling',
          simplePresent    : 'Enable',
          futurePerfect    : 'Enabled'
        });
      };

      this.canDisable = () => this.serverGroups.every((group) => !group.disabled);

      this.canEnable = () => this.serverGroups.every((group) => group.disabled);

      /***
       * View instantiation/synchronization
       */

      let retrieveServerGroups = () => {
        this.serverGroups = MultiselectModel.serverGroups.map(multiselectGroup => {
          let group = _.cloneDeep(multiselectGroup);
          let match = app.serverGroups.data.find(check => check.name === group.name && check.account === group.account && check.region === group.region);
          if (match) {
            group.instanceCounts = _.cloneDeep(match.instanceCounts);
            group.disabled = match.isDisabled;
          }
          return group;
        });
      };

      let multiselectWatcher = MultiselectModel.serverGroupsStream.subscribe(retrieveServerGroups);
      app.serverGroups.onRefresh($scope, retrieveServerGroups);

      retrieveServerGroups();

      $scope.$on('$destroy', () => {
        if (this.serverGroups.length !== 1) {
          MultiselectModel.clearAllServerGroups();
        }
        multiselectWatcher.unsubscribe();
      });

    }
  );
