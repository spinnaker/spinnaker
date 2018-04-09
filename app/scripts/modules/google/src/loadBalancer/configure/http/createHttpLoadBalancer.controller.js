'use strict';

const angular = require('angular');

import { TASK_MONITOR_BUILDER, V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

import { GCE_CACHE_REFRESH } from 'google/cache/cacheRefresh.component';
import { BackendServiceTemplate, HealthCheckTemplate, HostRuleTemplate, ListenerTemplate } from './templates';

import './httpLoadBalancerWizard.component.less';

module.exports = angular
  .module('spinnaker.deck.gce.loadBalancer.createHttp.controller', [
    require('angular-ui-bootstrap'),
    require('@uirouter/angularjs').default,
    require('./backendService/backendService.component.js').name,
    require('./basicSettings/basicSettings.component.js').name,
    GCE_CACHE_REFRESH,
    V2_MODAL_WIZARD_SERVICE,
    TASK_MONITOR_BUILDER,
    require('./commandBuilder.service.js').name,
    require('../../details/hostAndPathRules/hostAndPathRulesButton.component.js').name,
    require('./healthCheck/healthCheck.component.js').name,
    require('./hostRule/hostRule.component.js').name,
    require('./httpLoadBalancer.write.service.js').name,
    require('./listeners/listener.component.js').name,
    require('./transformer.service.js').name,
  ])
  .controller('gceCreateHttpLoadBalancerCtrl', function(
    $scope,
    $uibModal,
    $uibModalInstance,
    application,
    taskMonitorBuilder,
    loadBalancer,
    isNew,
    loadBalancerWriter,
    taskExecutor,
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

    let keyToTemplateMap = {
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

    let onApplicationRefresh = () => {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();

      let lb = this.command.loadBalancer;
      let newStateParams = {
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

    let onTaskComplete = () => {
      application.loadBalancers.refresh();
      application.loadBalancers.onNextRefresh($scope, onApplicationRefresh);
    };

    $scope.taskMonitor = this.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: this.application,
      title: (this.isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    this.submit = () => {
      let serializedCommands = gceHttpLoadBalancerTransformer.serialize(this.command, loadBalancer);
      let descriptor = this.isNew ? 'Create' : 'Update';

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
