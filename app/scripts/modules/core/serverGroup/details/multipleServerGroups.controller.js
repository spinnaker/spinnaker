'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.details.multipleServerGroups.controller', [
    require('angular-ui-router'),
    require('../serverGroup.write.service'),
    require('../../confirmationModal/confirmationModal.service'),
    require('../../insight/insightFilterState.model'),
    require('../../cluster/filter/multiselect.model'),
    require('./multipleServerGroup.component'),
  ])
  .controller('MultipleServerGroupsCtrl', function ($scope, $state, InsightFilterStateModel,
                                                    confirmationModalService, MultiselectModel,
                                                    serverGroupWriter, app) {

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

      let confirm = (submitMethod, verbs) => {
        let descriptor = getDescriptor(),
            monitorInterval = this.serverGroups.length * 1000;
        let taskMonitors = this.serverGroups.map(serverGroup => {
          return {
            application    : app,
            title          : serverGroup.name,
            submitMethod   : (params) => submitMethod(serverGroup, app, params),
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
        confirm(serverGroupWriter.destroyServerGroup, {
          presentContinuous: 'Destroying',
          simplePresent    : 'Destroy',
          futurePerfect    : 'Destroyed'
        });
      };

      this.disableServerGroups = () => {
        confirm(serverGroupWriter.disableServerGroup, {
          presentContinuous: 'Disabling',
          simplePresent    : 'Disable',
          futurePerfect    : 'Disabled'
        });
      };

      this.enableServerGroups = () => {
        confirm(serverGroupWriter.enableServerGroup, {
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
          let [match] = app.serverGroups.data.filter(check => check.name === group.name && check.account === group.account && check.region === group.region);
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
        multiselectWatcher.dispose();
      });

    }
  );
