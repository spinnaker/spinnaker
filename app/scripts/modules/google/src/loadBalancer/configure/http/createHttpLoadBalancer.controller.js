'use strict';

const angular = require('angular');

import { TaskMonitor } from '@spinnaker/core';

import { GCE_CACHE_REFRESH } from 'google/cache/cacheRefresh.component';
import { BackendServiceTemplate, HealthCheckTemplate, HostRuleTemplate, ListenerTemplate } from './templates';

import './httpLoadBalancerWizard.component.less';

module.exports = angular
  .module('spinnaker.deck.gce.loadBalancer.createHttp.controller', [
    require('angular-ui-bootstrap'),
    require('@uirouter/angularjs').default,
    require('./backendService/backendService.component').name,
    require('./basicSettings/basicSettings.component').name,
    GCE_CACHE_REFRESH,
    require('./commandBuilder.service').name,
    require('../../details/hostAndPathRules/hostAndPathRulesButton.component').name,
    require('./healthCheck/healthCheck.component').name,
    require('./hostRule/hostRule.component').name,
    require('./httpLoadBalancer.write.service').name,
    require('./listeners/listener.component').name,
    require('./transformer.service').name,
  ])
  .controller('gceCreateHttpLoadBalancerCtrl', function(
    $scope,
    $uibModal,
    $uibModalInstance,
    application,
    loadBalancer,
    isNew,
    gceHttpLoadBalancerWriter,
    $state,
    wizardSubFormValidation,
    gceHttpLoadBalancerCommandBuilder,
    gceHttpLoadBalancerTransformer,
  ) {
    this.application = application;
    this.isNew = isNew;
    this.modalDescriptor = this.isNew
      ? 'Create HTTP(S) load balancer'
      : `Edit ${loadBalancer.name}:global:${loadBalancer.account}`;

    this.pages = {
      location: require('./basicSettings/basicSettings.html'),
      listeners: require('./listeners/listeners.html'),
      defaultService: require('./defaultService/defaultService.html'),
      backendServices: require('./backendService/backendServices.html'),
      healthChecks: require('./healthCheck/healthChecks.html'),
      hostRules: require('./hostRule/hostRules.html'),
    };

    const keyToTemplateMap = {
      backendServices: BackendServiceTemplate,
      healthChecks: HealthCheckTemplate,
      hostRules: HostRuleTemplate,
      listeners: ListenerTemplate,
    };

    this.add = key => {
      this.command.loadBalancer[key].push(new keyToTemplateMap[key]());
    };

    this.remove = (key, index) => {
      this.command.loadBalancer[key].splice(index, 1);
    };

    const onApplicationRefresh = () => {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();

      const lb = this.command.loadBalancer;
      const newStateParams = {
        name: lb.urlMapName,
        accountId: lb.credentials,
        region: lb.region,
        provider: 'gce',
      };

      if (!$state.includes('**.loadBalancerDetails')) {
        $state.go('.loadBalancerDetails', newStateParams);
      } else {
        $state.go('^.loadBalancerDetails', newStateParams);
      }
    };

    const onTaskComplete = () => {
      application.loadBalancers.refresh();
      application.loadBalancers.onNextRefresh($scope, onApplicationRefresh);
    };

    $scope.taskMonitor = this.taskMonitor = new TaskMonitor({
      application: this.application,
      title: (this.isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    this.submit = () => {
      const serializedCommands = gceHttpLoadBalancerTransformer.serialize(this.command, loadBalancer);
      const descriptor = this.isNew ? 'Create' : 'Update';

      this.taskMonitor.submit(() =>
        gceHttpLoadBalancerWriter.upsertLoadBalancers(serializedCommands, application, descriptor),
      );
    };

    gceHttpLoadBalancerCommandBuilder.buildCommand({ isNew, originalLoadBalancer: loadBalancer }).then(command => {
      this.command = command;

      wizardSubFormValidation
        .config({ scope: $scope, form: 'form' })
        .register({ page: 'location', subForm: 'location' })
        .register({
          page: 'listeners',
          subForm: 'listeners',
          validators: [
            {
              watchString: 'ctrl.command.loadBalancer.listeners',
              validator: listeners => listeners.length > 0,
              collection: true,
            },
          ],
        })
        .register({ page: 'default-service', subForm: 'defaultService' })
        .register({ page: 'health-checks', subForm: 'healthChecks' })
        .register({ page: 'backend-services', subForm: 'backendServices' })
        .register({ page: 'host-rules', subForm: 'hostRules' });
    });

    this.cancel = $uibModalInstance.dismiss;
  });
