'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';

import { TaskMonitor } from '@spinnaker/core';

import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BACKENDSERVICE_BACKENDSERVICE_COMPONENT } from './backendService/backendService.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BASICSETTINGS_BASICSETTINGS_COMPONENT } from './basicSettings/basicSettings.component';
import { GCE_CACHE_REFRESH } from '../../../cache/cacheRefresh.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE } from './commandBuilder.service';
import { GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT } from '../../details/hostAndPathRules/hostAndPathRulesButton.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HEALTHCHECK_HEALTHCHECK_COMPONENT } from './healthCheck/healthCheck.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT } from './hostRule/hostRule.component';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE } from './httpLoadBalancer.write.service';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_LISTENERS_LISTENER_COMPONENT } from './listeners/listener.component';
import { BackendServiceTemplate, HealthCheckTemplate, HostRuleTemplate, ListenerTemplate } from './templates';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE } from './transformer.service';

import './httpLoadBalancerWizard.component.less';

export const GOOGLE_LOADBALANCER_CONFIGURE_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER =
  'spinnaker.deck.gce.loadBalancer.createHttp.controller';
export const name = GOOGLE_LOADBALANCER_CONFIGURE_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER; // for backwards compatibility
module(GOOGLE_LOADBALANCER_CONFIGURE_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER, [
  ANGULAR_UI_BOOTSTRAP,
  UIROUTER_ANGULARJS,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BACKENDSERVICE_BACKENDSERVICE_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_BASICSETTINGS_BASICSETTINGS_COMPONENT,
  GCE_CACHE_REFRESH,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_COMMANDBUILDER_SERVICE,
  GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULESBUTTON_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HEALTHCHECK_HEALTHCHECK_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HOSTRULE_HOSTRULE_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_HTTPLOADBALANCER_WRITE_SERVICE,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_LISTENERS_LISTENER_COMPONENT,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_TRANSFORMER_SERVICE,
]).controller('gceCreateHttpLoadBalancerCtrl', [
  '$scope',
  '$uibModal',
  '$uibModalInstance',
  'application',
  'loadBalancer',
  'isNew',
  'gceHttpLoadBalancerWriter',
  '$state',
  'wizardSubFormValidation',
  'gceHttpLoadBalancerCommandBuilder',
  'gceHttpLoadBalancerTransformer',
  function (
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

    this.add = (key) => {
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

    gceHttpLoadBalancerCommandBuilder.buildCommand({ isNew, originalLoadBalancer: loadBalancer }).then((command) => {
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
              validator: (listeners) => listeners.length > 0,
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
  },
]);
