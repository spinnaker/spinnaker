'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancer.createHttp.controller', [
  require('angular-ui-bootstrap'),
  require('angular-ui-router'),
  require('../../../../core/utils/lodash.js'),
  require('./templateGenerator.service.js'),
  require('./backendService/backendService.component.js'),
  require('./healthCheck/healthCheck.component.js'),
  require('./hostRule/hostRule.component.js'),
  require('./basicSettings/basicSettings.component.js'),
  require('../../../../core/task/monitor/taskMonitorService.js'),
  require('../../httpLoadBalancer.write.service.js'),
  require('../../../../core/modal/wizard/wizardSubFormValidation.service.js'),
  require('./editStateUtils.service.js'),
  require('../../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../elSevenUtils.service.js'),
])
  .controller('gceCreateHttpLoadBalancerCtrl', function (_, $scope, $uibModalInstance, application, taskMonitorService,
                                                         loadBalancer, isNew, loadBalancerWriter, taskExecutor,
                                                         gceHttpLoadBalancerWriter, $state, wizardSubFormValidation,
                                                         gceHttpLoadBalancerTemplateGenerator, $timeout, elSevenUtils,
                                                         gceHttpLoadBalancerEditStateUtils) {
    let {
      backendServiceTemplate,
      healthCheckTemplate,
      hostRuleTemplate,
      httpLoadBalancerTemplate } = gceHttpLoadBalancerTemplateGenerator;

    let keyToTemplateMap = {
      'backendServices': backendServiceTemplate,
      'healthChecks': healthCheckTemplate,
      'hostRules': hostRuleTemplate,
    };

    this.application = application;
    this.isNew = isNew;
    this.modalDescriptor = this.isNew
      ? 'Create HTTP(S) load balancer'
      : `Edit ${loadBalancer.name}:global:${loadBalancer.account}`;

    this.pages = {
      'location': require('./basicSettings/basicSettings.html'),
      'port': require('./port.html'),
      'backendServices': require('./backendService/backendServices.html'),
      'healthChecks': require('./healthCheck/healthChecks.html'),
      'hostRules': require('./hostRule/hostRules.html'),
    };

    wizardSubFormValidation
      .config({scope: $scope, form: 'form'})
      .register({page: 'location', subForm: 'location'})
      .register({page: 'port', subForm: 'port'})
      .register({
        page: 'health-checks',
        subForm: 'healthChecks',
        validators: [
          {
            watchString: 'ctrl.backingData.healthChecks',
            validator: (healthChecks) => healthChecks.length > 0,
            collection: true
          }
        ]
      })
      .register({
        page: 'backend-services',
        subForm: 'backendServices',
        validators: [
          {
            watchString: 'ctrl.backingData.backendServices',
            validator: (services) => services.length > 0,
            collection: true
          }
        ]
      })
      .register({page: 'host-rules', subForm: 'hostRules'});

    this.loadBalancer = loadBalancer || httpLoadBalancerTemplate();

    this.backingData = this.isNew
      ? { backendServices: [
            (function () {
              let template = backendServiceTemplate();
              template.useAsDefault = true;
              return template;
            })()],
          healthChecks: [healthCheckTemplate()],
          hostRules: [], }
      : gceHttpLoadBalancerEditStateUtils.getBackingData(this.loadBalancer);

    this.add = (key) => {
      this.backingData[key].push(keyToTemplateMap[key]());
    };

    this.remove = (key, index) => {
      let [removed] = this.backingData[key].splice(index, 1);

      if (removed.useAsDefault) {
        _.first(this.backingData[key]).useAsDefault = true;
      }
    };

    this.defaultServiceManager = (clickedService) => {
      // The checkbox operates more like a radio button: exactly one needs to be checked.
      if (clickedService.useAsDefault) {
        this.backingData.backendServices
          .filter(service => service !== clickedService)
          .forEach(service => service.useAsDefault = false);
      } else {
        clickedService.useAsDefault = true;
      }
    };

    let onApplicationRefresh = () => {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();

      let lb = this.loadBalancer;
      let newStateParams = {
        name: lb.name,
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
      let lb = this.loadBalancer;
      lb.hostRules = this.backingData.hostRules;
      lb.defaultService = this.backingData.backendServices.find(service => service.useAsDefault);
      if (!lb.credentials) {
        lb.credentials = lb.account;
      }

      let descriptor = this.isNew ? 'Create' : 'Update';

      this.taskMonitor.submit(() => gceHttpLoadBalancerWriter.upsertLoadBalancer(lb, application, descriptor));
    };

    $scope.$watch('ctrl.loadBalancer.certificate', (cert) => {
      if (!cert) {
        this.loadBalancer.portRange = 8080;
      } else {
        this.loadBalancer.portRange = 443;
      }
    });

    this.cancel = $uibModalInstance.dismiss;
  });
