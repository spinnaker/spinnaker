'use strict';

import {HealthCheckTemplate, BackendServiceTemplate, HostRuleTemplate, ListenerTemplate} from './templates';
import modalWizardServiceModule from 'core/modal/wizard/v2modalWizard.service';

let angular = require('angular');
require('./httpLoadBalancerWizard.component.less');

module.exports = angular.module('spinnaker.deck.gce.loadBalancer.createHttp.controller', [
  require('angular-ui-bootstrap'),
  require('angular-ui-router'),
  require('./backendService/backendService.component.js'),
  require('./basicSettings/basicSettings.component.js'),
  require('google/cache/cacheRefresh.component.js'),
  require('core/modal/wizard/wizardSubFormValidation.service.js'),
  modalWizardServiceModule,
  require('core/task/monitor/taskMonitorService.js'),
  require('./commandBuilder.service.js'),
  require('../../details/hostAndPathRules/hostAndPathRulesButton.component.js'),
  require('../../elSevenUtils.service.js'),
  require('./healthCheck/healthCheck.component.js'),
  require('./hostRule/hostRule.component.js'),
  require('./httpLoadBalancer.write.service.js'),
  require('./listeners/listener.component.js'),
  require('./transformer.service.js'),
])
  .controller('gceCreateHttpLoadBalancerCtrl', function ($scope, $uibModal, settings, $uibModalInstance, application, taskMonitorService,
                                                         loadBalancer, isNew, loadBalancerWriter, taskExecutor,
                                                         gceHttpLoadBalancerWriter, $state, wizardSubFormValidation,
                                                         gceHttpLoadBalancerCommandBuilder, gceHttpLoadBalancerTransformer) {
    this.application = application;
    this.isNew = isNew;
    this.modalDescriptor = this.isNew
      ? 'Create HTTP(S) load balancer'
      : `Edit ${loadBalancer.name}:global:${loadBalancer.account}`;

    this.pages = {
      'location': require('./basicSettings/basicSettings.html'),
      'listeners': require('./listeners/listeners.html'),
      'defaultService': require('./defaultService/defaultService.html'),
      'backendServices': require('./backendService/backendServices.html'),
      'healthChecks': require('./healthCheck/healthChecks.html'),
      'hostRules': require('./hostRule/hostRules.html'),
    };

    let keyToTemplateMap = {
      'backendServices': BackendServiceTemplate,
      'healthChecks': HealthCheckTemplate,
      'hostRules': HostRuleTemplate,
      'listeners': ListenerTemplate,
    };

    this.add = (key) => {
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

    $scope.taskMonitor = this.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: this.application,
      title: (this.isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    this.submit = () => {
      let serializedCommands = gceHttpLoadBalancerTransformer.serialize(this.command, loadBalancer);
      let descriptor = this.isNew ? 'Create' : 'Update';

      this.taskMonitor.submit(() => gceHttpLoadBalancerWriter.upsertLoadBalancers(serializedCommands, application, descriptor));
    };

    this.preview = () => {
      let [preview] = gceHttpLoadBalancerTransformer.serialize(this.command, loadBalancer);

      $uibModal.open({
        templateUrl: require('../../details/hostAndPathRules/hostAndPathRules.modal.html'),
        controller: 'gceHostAndPathRulesCtrl',
        controllerAs: 'ctrl',
        size: 'lg',
        resolve: {
          hostRules: () => preview.hostRules,
          defaultService: () => preview.defaultService,
          loadBalancerName: () => preview.urlMapName,
        }
      });
    };

    gceHttpLoadBalancerCommandBuilder.buildCommand({ isNew, originalLoadBalancer: loadBalancer})
      .then((command) => {
        this.command = command;

        wizardSubFormValidation
          .config({scope: $scope, form: 'form'})
          .register({page: 'location', subForm: 'location'})
          .register({
            page: 'listeners',
            subForm: 'listeners',
            validators: [
              {
                watchString: 'ctrl.command.loadBalancer.listeners',
                validator: (listeners) => listeners.length > 0,
                collection: true
              }
            ]
          })
          .register({page: 'default-service', subForm: 'defaultService'})
          .register({page: 'health-checks', subForm: 'healthChecks'})
          .register({page: 'backend-services', subForm: 'backendServices'})
          .register({page: 'host-rules', subForm: 'hostRules'});
      });

    this.cancel = $uibModalInstance.dismiss;
  });
