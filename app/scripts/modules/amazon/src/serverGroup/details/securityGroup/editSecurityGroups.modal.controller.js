'use strict';

const angular = require('angular');
import _ from 'lodash';

import { SECURITY_GROUP_READER, SERVER_GROUP_WRITER, TaskMonitor, FirewallLabels } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.amazon.serverGroup.details.securityGroup.editSecurityGroups.modal.controller', [
    SERVER_GROUP_WRITER,
    SECURITY_GROUP_READER,
  ])
  .controller('EditSecurityGroupsCtrl', ['$scope', '$uibModalInstance', 'serverGroupWriter', 'securityGroupReader', 'application', 'serverGroup', 'securityGroups', function(
    $scope,
    $uibModalInstance,
    serverGroupWriter,
    securityGroupReader,
    application,
    serverGroup,
    securityGroups,
  ) {
    this.command = {
      securityGroups: securityGroups.slice(0).sort((a, b) => a.name.localeCompare(b.name)),
    };

    this.state = {
      securityGroupsLoaded: false,
      submitting: false,
      verification: {},
    };

    this.infiniteScroll = {
      currentItems: 20,
    };

    this.addMoreItems = () => (this.infiniteScroll.currentItems += 20);

    this.resetCurrentItems = () => (this.infiniteScroll.currentItems = 20);

    this.isValid = () => this.state.verification.verified;

    securityGroupReader.getAllSecurityGroups().then(allGroups => {
      let account = serverGroup.account,
        region = serverGroup.region,
        vpcId = serverGroup.vpcId;
      this.availableSecurityGroups = _.get(allGroups, [account, 'aws', region].join('.'), [])
        .filter(group => group.vpcId === vpcId)
        .sort((a, b) => {
          if (this.command.securityGroups.some(g => g.id === a.id)) {
            return -1;
          }
          if (this.command.securityGroups.some(g => g.id === b.id)) {
            return 1;
          }
          return a.name.localeCompare(b.name);
        });
      this.state.securityGroupsLoaded = true;
    });

    this.serverGroup = serverGroup;

    this.taskMonitor = new TaskMonitor({
      application: application,
      title: `Update ${FirewallLabels.get('Firewalls')} for ${serverGroup.name}`,
      modalInstance: $uibModalInstance,
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    this.submit = () => {
      var submitMethod = () => {
        this.state.submitting = true;
        return serverGroupWriter.updateSecurityGroups(serverGroup, this.command.securityGroups, application);
      };

      this.taskMonitor.submit(submitMethod);
    };

    this.cancel = $uibModalInstance.dismiss;
  }]);
