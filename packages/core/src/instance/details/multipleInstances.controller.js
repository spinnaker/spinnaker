'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';

import { ConfirmationModalService } from '../../confirmationModal';
import { InstanceWriter } from '../instance.write.service';
import { CORE_INSTANCE_DETAILS_MULTIPLEINSTANCESERVERGROUP_DIRECTIVE } from './multipleInstanceServerGroup.directive';
import { ClusterState } from '../../state';

export const CORE_INSTANCE_DETAILS_MULTIPLEINSTANCES_CONTROLLER =
  'spinnaker.core.instance.details.multipleInstances.controller';
export const name = CORE_INSTANCE_DETAILS_MULTIPLEINSTANCES_CONTROLLER; // for backwards compatibility
module(CORE_INSTANCE_DETAILS_MULTIPLEINSTANCES_CONTROLLER, [
  UIROUTER_ANGULARJS,
  CORE_INSTANCE_DETAILS_MULTIPLEINSTANCESERVERGROUP_DIRECTIVE,
]).controller('MultipleInstancesCtrl', [
  '$scope',
  '$state',
  'app',
  function ($scope, $state, app) {
    this.selectedGroups = [];

    /**
     * Actions
     */

    const getDescriptor = () => {
      let descriptor = this.instancesCount + ' instance';
      if (this.instancesCount > 1) {
        descriptor += 's';
      }
      return descriptor;
    };

    const confirm = (submitMethod, verbs, body) => {
      const descriptor = getDescriptor();
      const taskMonitor = {
        application: app,
        title: verbs.presentContinuous + ' ' + descriptor,
      };

      ConfirmationModalService.confirm({
        header: 'Really ' + verbs.simplePresent.toLowerCase() + ' ' + descriptor + '?',
        buttonText: verbs.simplePresent + ' ' + descriptor,
        verificationLabel:
          'Verify the number of instances (<span class="verification-text">' +
          this.instancesCount +
          '</span>) to be ' +
          verbs.futurePerfect.toLowerCase(),
        textToVerify: this.instancesCount + '',
        taskMonitorConfig: taskMonitor,
        body: body,
        submitMethod: submitMethod,
      });
    };

    this.terminateInstances = () => {
      const submitMethod = () => InstanceWriter.terminateInstances(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Terminating',
        simplePresent: 'Terminate',
        futurePerfect: 'Terminated',
      });
    };

    this.canTerminateInstancesAndShrinkServerGroups = () => {
      return !this.selectedGroups.some((group) => {
        // terminateInstancesAndShrinkServerGroups is aws-only
        return group.cloudProvider !== 'aws';
      });
    };

    this.terminateInstancesAndShrinkServerGroups = () => {
      const submitMethod = () => InstanceWriter.terminateInstancesAndShrinkServerGroups(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Terminating',
        simplePresent: 'Terminate',
        futurePerfect: 'Terminated',
      });
    };

    this.rebootInstances = () => {
      const submitMethod = () => InstanceWriter.rebootInstances(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Rebooting',
        simplePresent: 'Reboot',
        futurePerfect: 'Rebooted',
      });
    };

    const allDiscoveryHealthsMatch = (state) => {
      return this.selectedGroups.every((group) => {
        return group.instances.every((instance) => {
          const discoveryHealth = instance.health.filter(function (health) {
            return health.type === 'Discovery';
          });
          return discoveryHealth.length ? discoveryHealth[0].state === state : false;
        });
      });
    };

    this.canRegisterWithDiscovery = () => allDiscoveryHealthsMatch('OutOfService');

    this.canDeregisterWithDiscovery = () => allDiscoveryHealthsMatch('Up') || allDiscoveryHealthsMatch('Down');

    this.registerWithDiscovery = () => {
      const submitMethod = () => InstanceWriter.enableInstancesInDiscovery(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Registering',
        simplePresent: 'Register',
        futurePerfect: 'Registered',
      });
    };

    this.deregisterWithDiscovery = () => {
      const submitMethod = () => InstanceWriter.disableInstancesInDiscovery(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Deregistering',
        simplePresent: 'Deregister',
        futurePerfect: 'Deregistered',
      });
    };

    const getAllLoadBalancers = () => {
      if (!this.selectedGroups.length) {
        return [];
      }
      const base = this.selectedGroups[0].loadBalancers.sort().join(' ');
      if (this.selectedGroups.every((group) => group.loadBalancers.sort().join(' ') === base)) {
        return this.selectedGroups[0].loadBalancers;
      }
      return [];
    };

    this.canRegisterWithLoadBalancers = () => {
      return (
        getAllLoadBalancers().length !== 0 && // !== 0 so we always return a boolean
        this.selectedGroups.every((group) =>
          group.instances.every((instance) => instance.health.every((health) => health.type !== 'LoadBalancer')),
        )
      );
    };

    this.canDeregisterFromLoadBalancers = () => {
      const allLoadBalancers = getAllLoadBalancers().sort().join(' ');
      return this.selectedGroups.every((group) => {
        return group.instances.every((instance) => {
          return instance.health.some((health) => {
            return (
              health.type === 'LoadBalancer' &&
              allLoadBalancers ===
                health.loadBalancers
                  .map((lb) => lb.name)
                  .sort()
                  .join(' ')
            );
          });
        });
      });
    };

    this.registerWithLoadBalancers = () => {
      const allLoadBalancers = getAllLoadBalancers().sort();
      const submitMethod = () =>
        InstanceWriter.registerInstancesWithLoadBalancer(this.selectedGroups, app, allLoadBalancers);
      confirm(
        submitMethod,
        {
          presentContinuous: 'Registering',
          simplePresent: 'Register',
          futurePerfect: 'Registered',
        },
        `<p>Instances will be registered with the following load balancers: <b>${allLoadBalancers.join(', ')}</b></p>`,
      );
    };

    this.deregisterFromLoadBalancers = () => {
      const allLoadBalancers = getAllLoadBalancers().sort();
      const submitMethod = () =>
        InstanceWriter.deregisterInstancesFromLoadBalancer(this.selectedGroups, app, allLoadBalancers);
      confirm(
        submitMethod,
        {
          presentContinuous: 'Deregistering',
          simplePresent: 'Deregister',
          futurePerfect: 'Deregistered',
        },
        `<p>Instances will be deregistered from the following load balancers: <b>${allLoadBalancers.join(
          ', ',
        )}</b></p>`,
      );
    };

    /***
     * View instantiation/synchronization
     */

    function getServerGroup(group) {
      return app.serverGroups.data.find(
        (serverGroup) =>
          serverGroup.name === group.serverGroup &&
          serverGroup.account === group.account &&
          serverGroup.region === group.region,
      );
    }

    function getInstanceDetails(group, instanceId) {
      const serverGroup = getServerGroup(group);

      if (!serverGroup) {
        return null;
      }

      return serverGroup.instances.find((instance) => instance.id === instanceId) || {};
    }

    const makeInstanceModel = (group, instanceId) => {
      const instance = getInstanceDetails(group, instanceId);
      return {
        id: instanceId,
        availabilityZone: instance.availabilityZone,
        health: instance.health || [],
        healthState: instance.healthState,
        name: instance.name,
      };
    };

    const makeServerGroupModel = (group) => {
      const parentServerGroup = getServerGroup(group);
      const loadBalancers = parentServerGroup ? parentServerGroup.loadBalancers : [];

      return {
        cloudProvider: group.cloudProvider,
        serverGroup: group.serverGroup,
        loadBalancers: loadBalancers,
        account: group.account,
        region: group.region,
        instanceIds: group.instanceIds,
        instances: group.instanceIds.map((instanceId) => makeInstanceModel(group, instanceId)),
      };
    };

    const countInstances = () => {
      return ClusterState.multiselectModel.instanceGroups.reduce((acc, group) => acc + group.instanceIds.length, 0);
    };

    const retrieveInstances = () => {
      this.instancesCount = countInstances();
      this.selectedGroups = ClusterState.multiselectModel.instanceGroups
        .filter((group) => group.instanceIds.length)
        .map(makeServerGroupModel);
    };

    const multiselectWatcher = ClusterState.multiselectModel.instancesStream.subscribe(retrieveInstances);
    app.serverGroups.onRefresh($scope, retrieveInstances);

    retrieveInstances();

    $scope.$on('$destroy', () => {
      ClusterState.multiselectModel.deselectAllInstances();
      multiselectWatcher.unsubscribe();
    });
  },
]);
