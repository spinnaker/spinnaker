'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.instance.details.multipleInstances.controller', [
  require('angular-ui-router'),
  require('../instance.write.service'),
  require('../../confirmationModal/confirmationModal.service'),
  require('../../insight/insightFilterState.model'),
  require('../../cluster/filter/multiselect.model'),
  require('./multipleInstanceServerGroup.directive'),
])
  .controller('MultipleInstancesCtrl', function ($scope, $state, InsightFilterStateModel,
                                                 confirmationModalService, MultiselectModel,
                                                 instanceWriter, app) {

    this.InsightFilterStateModel = InsightFilterStateModel;
    this.selectedGroups = [];

    /**
     * Actions
     */

    let getDescriptor = () => {
      let descriptor = this.instancesCount + ' instance';
      if (this.instancesCount > 1) {
        descriptor += 's';
      }
      return descriptor;
    };

    let confirm = (submitMethod, verbs, body) => {
      let descriptor = getDescriptor();
      var taskMonitor = {
        application: app,
        title: verbs.presentContinuous + ' ' + descriptor,
      };

      confirmationModalService.confirm({
        header: 'Really ' + verbs.simplePresent.toLowerCase() + ' ' + descriptor + '?',
        buttonText: verbs.simplePresent + ' ' + descriptor,
        verificationLabel: 'Verify the number of instances (<span class="verification-text">' + this.instancesCount + '</span>) to be ' + verbs.futurePerfect.toLowerCase(),
        textToVerify: this.instancesCount + '',
        taskMonitorConfig: taskMonitor,
        body: body,
        submitMethod: submitMethod
      });
    };

    this.terminateInstances = () => {
      let submitMethod = () => instanceWriter.terminateInstances(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Terminating',
        simplePresent: 'Terminate',
        futurePerfect: 'Terminated'
      });
    };

    this.canTerminateInstancesAndShrinkServerGroups = () => {
      return !this.selectedGroups.some((group) => {
        // terminateInstancesAndShrinkServerGroups is aws-only
        return group.cloudProvider !== 'aws';
      });
    };

    this.terminateInstancesAndShrinkServerGroups = () => {
      let submitMethod = () => instanceWriter.terminateInstancesAndShrinkServerGroups(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Terminating',
        simplePresent: 'Terminate',
        futurePerfect: 'Terminated'
      });
    };

    this.rebootInstances = () => {
      let submitMethod = () => instanceWriter.rebootInstances(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Rebooting',
        simplePresent: 'Reboot',
        futurePerfect: 'Rebooted'
      });
    };

    let allDiscoveryHealthsMatch = (state) => {
      return this.selectedGroups.every((group) => {
        return group.instances.every((instance) => {
          var discoveryHealth = instance.health.filter(function(health) {
            return health.type === 'Discovery';
          });
          return discoveryHealth.length ? discoveryHealth[0].state === state : false;
        });
      });
    };

    this.canRegisterWithDiscovery = () => allDiscoveryHealthsMatch('OutOfService');

    this.canDeregisterWithDiscovery = () => allDiscoveryHealthsMatch('Up');

    this.registerWithDiscovery = () => {
      let submitMethod = () => instanceWriter.enableInstancesInDiscovery(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Registering',
        simplePresent: 'Register',
        futurePerfect: 'Registered'
      });
    };

    this.deregisterWithDiscovery = () => {
      let submitMethod = () => instanceWriter.disableInstancesInDiscovery(this.selectedGroups, app);
      confirm(submitMethod, {
        presentContinuous: 'Deregistering',
        simplePresent: 'Deregister',
        futurePerfect: 'Deregistered'
      });
    };

    let getAllLoadBalancers = () => {
      if (!this.selectedGroups.length) {
        return [];
      }
      let base = this.selectedGroups[0].loadBalancers.sort().join(' ');
      if (this.selectedGroups.every((group) => group.loadBalancers.sort().join(' ') === base)) {
        return this.selectedGroups[0].loadBalancers;
      }
      return [];
    };

    this.canRegisterWithLoadBalancers = () => {
      return getAllLoadBalancers().length !== 0 && // !== 0 so we always return a boolean
        this.selectedGroups.every((group) =>
          group.instances.every((instance) =>
            instance.health.every((health) =>
            health.type !== 'LoadBalancer')));
    };

    this.canDeregisterFromLoadBalancers = () => {
      let allLoadBalancers = getAllLoadBalancers().sort().join(' ');
      return this.selectedGroups.every((group) => {
        return group.instances.every((instance) => {
          return instance.health.some((health) => {
            return health.type === 'LoadBalancer' &&
              allLoadBalancers === health.loadBalancers.map((lb) => lb.name).sort().join(' ');
          });
        });
      });
    };

    this.registerWithLoadBalancers = () => {
      let allLoadBalancers = getAllLoadBalancers().sort();
      let submitMethod = () => instanceWriter.registerInstancesWithLoadBalancer(this.selectedGroups, app, allLoadBalancers);
      confirm(submitMethod, {
        presentContinuous: 'Registering',
        simplePresent: 'Register',
        futurePerfect: 'Registered'
      },
        `<p>Instances will be registered with the following load balancers: <b>${allLoadBalancers.join(', ')}</b></p>`
      );
    };

    this.deregisterFromLoadBalancers = () => {
      let allLoadBalancers = getAllLoadBalancers().sort();
      let submitMethod = () => instanceWriter.deregisterInstancesFromLoadBalancer(this.selectedGroups, app, allLoadBalancers);
      confirm(submitMethod, {
        presentContinuous: 'Deregistering',
        simplePresent: 'Deregister',
        futurePerfect: 'Deregistered'
      },
        `<p>Instances will be deregistered from the following load balancers: <b>${allLoadBalancers.join(', ')}</b></p>`
      );
    };

    /***
     * View instantiation/synchronization
     */

    function getServerGroup(group) {
      let [serverGroup] = app.serverGroups.data.filter((serverGroup) => serverGroup.name === group.serverGroup &&
          serverGroup.account === group.account && serverGroup.region === group.region);

      return serverGroup;
    }

    function getInstanceDetails(group, instanceId) {
      let serverGroup = getServerGroup(group);

      if (!serverGroup) {
        return null;
      }

      let [instance] = serverGroup.instances.filter((instance) => instance.id === instanceId);
      return instance || {};
    }

    let makeInstanceModel = (group, instanceId) => {
      let instance = getInstanceDetails(group, instanceId);
      return {
        id: instanceId,
        availabilityZone: instance.availabilityZone,
        health: instance.health || [],
        healthState: instance.healthState,
      };
    };

    let makeServerGroupModel = (group) => {
      let parentServerGroup = getServerGroup(group),
          loadBalancers = parentServerGroup ? parentServerGroup.loadBalancers : [];

      return {
        cloudProvider: group.cloudProvider,
        serverGroup: group.serverGroup,
        loadBalancers: loadBalancers,
        account: group.account,
        region: group.region,
        instanceIds: group.instanceIds,
        instances: group.instanceIds.map((instanceId) => makeInstanceModel(group, instanceId))
      };
    };

    let countInstances = () => {
      return MultiselectModel.instanceGroups.reduce((acc, group) => acc + group.instanceIds.length, 0);
    };

    let retrieveInstances = () => {
      this.instancesCount = countInstances();
      this.selectedGroups = MultiselectModel.instanceGroups
        .filter((group) => group.instanceIds.length)
        .map(makeServerGroupModel);
    };

    let multiselectWatcher = MultiselectModel.instancesStream.subscribe(retrieveInstances);
    app.serverGroups.onRefresh($scope, retrieveInstances);

    retrieveInstances();

    $scope.$on('$destroy', () => {
      MultiselectModel.deselectAllInstances();
      multiselectWatcher.dispose();
    });

  }
);
