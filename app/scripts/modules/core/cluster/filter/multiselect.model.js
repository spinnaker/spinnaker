'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.cluster.filter.multiselect.model', [
    require('angular-ui-router'),
    require('../../utils/rx.js'),
    require('../../utils/lodash'),
    require('./clusterFilter.model'),
  ])
  .factory('MultiselectModel', function (_, rx, $state, ClusterFilterModel) {

    this.instanceGroups = [];
    this.instancesStream = new rx.Subject();

    this.serverGroups = [];
    this.serverGroupsStream = new rx.Subject();

    this.syncNavigation = () => {
      if ($state.includes('**.multipleInstances') && !ClusterFilterModel.sortFilter.multiselect) {
        this.deselectAllInstances();
        $state.go('^');
        return;
      }

      if ($state.includes('**.multipleServerGroups') && !ClusterFilterModel.sortFilter.multiselect) {
        this.clearAllServerGroups();
        $state.go('^');
        return;
      }

      let instancesSelected = this.instanceGroups.reduce((acc, group) => acc + group.instanceIds.length, 0);

      if ($state.includes('**.multipleInstances') && !instancesSelected) {
        $state.go('^');
      }
      if (!$state.includes('**.multipleInstances') && instancesSelected) {
        if ($state.includes('**.clusters.*')) {
          // from a child state, e.g. instanceDetails
          $state.go('^.multipleInstances');
        } else {
          $state.go('.multipleInstances');
        }
      }
      if ($state.includes('**.multipleServerGroups') && !this.serverGroups.length) {
        $state.go('^');
        return;
      }
      if (!$state.includes('**.multipleServerGroups') && this.serverGroups.length) {
        if ($state.includes('**.clusters.*')) {
          $state.go('^.multipleServerGroups');
        } else {
          $state.go('.multipleServerGroups');
        }
      }
    };

    this.deselectAllInstances = () => {
      this.instanceGroups.forEach((instanceGroup) => {
        instanceGroup.instanceIds.length = 0;
        instanceGroup.selectAll = false;
      });
    };

    this.clearAllInstanceGroups = () => {
      this.instanceGroups.length = 0;
      this.instancesStream.onNext();
    };

    this.clearAllServerGroups = () => {
      this.serverGroups.length = 0;
      this.serverGroupsStream.onNext();
    };

    this.clearAll = () => {
      this.clearAllInstanceGroups();
      this.clearAllServerGroups();
    };

    this.getOrCreateInstanceGroup = (serverGroup) => {
      let serverGroupName = serverGroup.name,
          account = serverGroup.account,
          region = serverGroup.region,
          cloudProvider = serverGroup.type;
      let [result] = this.instanceGroups.filter((instanceGroup) => {
        return instanceGroup.serverGroup === serverGroupName &&
          instanceGroup.account === account &&
          instanceGroup.region === region &&
          instanceGroup.cloudProvider === cloudProvider;
      });
      if (!result) {
        // when creating a new group, include an instance ID if we're deep-linked into the details view
        let params = $state.params;
        let instanceIds = (serverGroup.instances || [])
          .filter((instance) => instance.provider === params.provider && instance.id === params.instanceId)
          .map((instance) => instance.id);
        result = {
          serverGroup: serverGroupName,
          account: account,
          region: region,
          cloudProvider: cloudProvider,
          instanceIds: instanceIds,
          instances: [], // populated by details controller
          selectAll: false,
        };
        this.instanceGroups.push(result);
      }
      return result;
    };

    this.makeServerGroupKey = (serverGroup) =>
      [serverGroup.type, serverGroup.account, serverGroup.region, serverGroup.name, serverGroup.category].join(':');

    this.serverGroupIsSelected = (serverGroup) => {
      if (!this.serverGroups.length) {
        return false;
      }
      let key = this.makeServerGroupKey(serverGroup);
      return this.serverGroups.filter((sg) => sg.key === key).length > 0;
    };

    this.toggleServerGroup = (serverGroup) => {
      if (!ClusterFilterModel.sortFilter.multiselect) {
        let params = {
          provider: serverGroup.type,
          accountId: serverGroup.account,
          region: serverGroup.region,
          serverGroup: serverGroup.name,
          job: serverGroup.name,
        };
        if ($state.includes('**.clusters.*')) {
          $state.go('^.' + serverGroup.category, params);
        } else {
          $state.go('.' + serverGroup.category, params);
        }
        return;
      }
      this.deselectAllInstances();
      let key = this.makeServerGroupKey(serverGroup),
          [selected] = this.serverGroups.filter((sg) => sg.key === key);
      if (selected) {
        this.serverGroups.splice(this.serverGroups.indexOf(selected), 1);
      } else {
        this.serverGroups.push({
          key: key,
          account: serverGroup.account,
          region: serverGroup.region,
          provider: serverGroup.type,
          name: serverGroup.name,
        });
      }
      this.serverGroupsStream.onNext();
      this.syncNavigation();
    };

    this.toggleInstance = (serverGroup, instanceId) => {
      if (!ClusterFilterModel.sortFilter.multiselect) {
        let params = {provider: serverGroup.type, instanceId: instanceId};
        if ($state.includes('**.clusters.*')) {
          $state.go('^.instanceDetails', params);
        } else {
          $state.go('.instanceDetails', params);
        }
        return;
      }
      this.clearAllServerGroups();
      let group = this.getOrCreateInstanceGroup(serverGroup);
      if (group.instanceIds.indexOf(instanceId) > -1) {
        group.instanceIds.splice(group.instanceIds.indexOf(instanceId), 1);
        group.selectAll = false;
      } else {
        group.instanceIds.push(instanceId);
      }
      this.instancesStream.onNext();
      this.syncNavigation();
    };

    this.toggleSelectAll = (serverGroup, allInstanceIds) => {
      let group = this.getOrCreateInstanceGroup(serverGroup);
      group.selectAll = !group.selectAll;
      group.instanceIds = group.selectAll ? allInstanceIds : [];
      if (group.selectAll) {
        this.clearAllServerGroups();
      }
      this.instancesStream.onNext();
      this.syncNavigation();
    };

    this.instanceIsSelected = (serverGroup, instanceId) => {
      let group = this.getOrCreateInstanceGroup(serverGroup);
      return group.instanceIds.indexOf(instanceId) > -1;
    };

    return this;
  });
