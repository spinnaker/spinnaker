'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  ACCOUNT_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  LOAD_BALANCER_WRITE_SERVICE,
  NAMING_SERVICE,
  SECURITY_GROUP_READER,
  SUBNET_READ_SERVICE,
  TASK_MONITOR_BUILDER,
  V2_MODAL_WIZARD_SERVICE
} from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { AWS_LOAD_BALANCER_TRANFORMER } from 'amazon/loadBalancer/loadBalancer.transformer';
import { SUBNET_SELECT_FIELD_COMPONENT } from 'amazon/subnet/subnetSelectField.component';

module.exports = angular.module('spinnaker.amazon.loadBalancer.classic.create.controller', [
  require('@uirouter/angularjs').default,
  LOAD_BALANCER_WRITE_SERVICE,
  ACCOUNT_SERVICE,
  AWS_LOAD_BALANCER_TRANFORMER,
  SECURITY_GROUP_READER,
  V2_MODAL_WIZARD_SERVICE,
  TASK_MONITOR_BUILDER,
  SUBNET_READ_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  NAMING_SERVICE,
  require('../common/loadBalancerAvailabilityZoneSelector.directive.js'),
  SUBNET_SELECT_FIELD_COMPONENT,
])
  .controller('awsCreateClassicLoadBalancerCtrl', function($scope, $uibModalInstance, $state,
                                                           accountService, awsLoadBalancerTransformer, securityGroupReader,
                                                           cacheInitializer, infrastructureCaches,
                                                           v2modalWizardService, loadBalancerWriter, taskMonitorBuilder,
                                                           subnetReader, namingService,
                                                           application, loadBalancer, isNew, forPipelineConfig) {

    var ctrl = this;
    ctrl.pages = {
      location: require('../common/createLoadBalancerLocation.html'),
      securityGroups: require('../common/securityGroups.html'),
      listeners: require('./listeners.html'),
      healthCheck: require('./healthCheck.html'),
      advancedSettings: require('./advancedSettings.html'),
    };

    ctrl.isNew = isNew;
    ctrl.application = application;
    // if this controller is used in the context of "Create Load Balancer" stage,
    // then forPipelineConfig flag will be true. In that case, the Load Balancer
    // modal dialog will just return the Load Balancer object.
    ctrl.forPipelineConfig = forPipelineConfig;

    ctrl.viewState = {
      accountsLoaded: false,
      currentItems: 25,
      hideInternalFlag: false,
      internalFlagToggled: false,
      refreshingSecurityGroups: false,
      removedSecurityGroups: [],
      securityGroupRefreshTime: infrastructureCaches.get('securityGroups').getStats().ageMax,
      securityGroupsLoaded: false,
      submitButtonLabel: forPipelineConfig ? (isNew ? 'Add' : 'Done') : (isNew ? 'Create' : 'Update'),
      submitting: false,
    };

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();
      var newStateParams = {
        name: ctrl.loadBalancerCommand.name,
        accountId: ctrl.loadBalancerCommand.credentials,
        region: ctrl.loadBalancerCommand.region,
        vpcId: ctrl.loadBalancerCommand.vpcId,
        provider: 'aws',
      };

      if (!$state.includes('**.loadBalancerDetails')) {
        $state.go('.loadBalancerDetails', newStateParams);
      } else {
        $state.go('^.loadBalancerDetails', newStateParams);
      }
    }

    function onTaskComplete() {
      application.loadBalancers.refresh();
      application.loadBalancers.onNextRefresh($scope, onApplicationRefresh);
    }

    ctrl.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete
    });

    var allSecurityGroups = {},
        defaultSecurityGroups = [];

    function initializeEditMode() {
      if (ctrl.loadBalancerCommand.vpcId) {
        preloadSecurityGroups().then(function() {
          updateAvailableSecurityGroups([ctrl.loadBalancerCommand.vpcId]);
        });
      }
    }

    function initializeCreateMode() {
      preloadSecurityGroups();
      if (AWSProviderSettings) {
        if (AWSProviderSettings.defaultSecurityGroups) {
          defaultSecurityGroups = AWSProviderSettings.defaultSecurityGroups;
        }
        if (AWSProviderSettings.loadBalancers && AWSProviderSettings.loadBalancers.inferInternalFlagFromSubnet) {
          delete ctrl.loadBalancerCommand.isInternal;
          ctrl.viewState.hideInternalFlag = true;
        }
      }
      accountService.listAccounts('aws').then(function (accounts) {
        ctrl.accounts = accounts;
        ctrl.viewState.accountsLoaded = true;
        ctrl.accountUpdated();
      });
    }

    function preloadSecurityGroups() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        allSecurityGroups = securityGroups;
        ctrl.viewState.securityGroupsLoaded = true;
      });
    }

    function initializeController() {
      setSecurityGroupRefreshTime();
      if (loadBalancer) {
        if (forPipelineConfig) {
          ctrl.loadBalancerCommand = loadBalancer;
          initializeCreateMode();
        } else {
          ctrl.loadBalancerCommand = awsLoadBalancerTransformer.convertClassicLoadBalancerForEditing(loadBalancer);
          initializeEditMode();
        }
        if (isNew) {
          var nameParts = namingService.parseLoadBalancerName(ctrl.loadBalancerCommand.name);
          ctrl.loadBalancerCommand.stack = nameParts.stack;
          ctrl.loadBalancerCommand.detail = nameParts.freeFormDetails;
          delete ctrl.loadBalancerCommand.name;
        }
      } else {
        ctrl.loadBalancerCommand = awsLoadBalancerTransformer.constructNewClassicLoadBalancerTemplate(application);
      }
      if (isNew) {
        updateLoadBalancerNames();
        initializeCreateMode();
      }
    }

    function availableGroupsSorter(a, b) {
      if (defaultSecurityGroups) {
        if (defaultSecurityGroups.includes(a.name)) {
          return -1;
        }
        if (defaultSecurityGroups.includes(b.name)) {
          return 1;
        }
      }
      return ctrl.loadBalancerCommand.securityGroups.includes(a.id) ? -1 :
        ctrl.loadBalancerCommand.securityGroups.includes(b.id) ? 1 :
          0;
    }

    function updateAvailableSecurityGroups(availableVpcIds) {
      var account = ctrl.loadBalancerCommand.credentials,
        region = ctrl.loadBalancerCommand.region;

      if (account && region && allSecurityGroups[account] && allSecurityGroups[account].aws[region]) {
        ctrl.availableSecurityGroups = _.filter(allSecurityGroups[account].aws[region], function(securityGroup) {
          return availableVpcIds.includes(securityGroup.vpcId);
        }).sort(availableGroupsSorter); // push existing groups to top
        ctrl.existingSecurityGroupNames = _.map(ctrl.availableSecurityGroups, 'name');
        var existingNames = defaultSecurityGroups.filter(function(defaultName) {
          return ctrl.existingSecurityGroupNames.includes(defaultName);
        });
        ctrl.loadBalancerCommand.securityGroups.forEach(function(securityGroup) {
          if (!ctrl.existingSecurityGroupNames.includes(securityGroup)) {
            var matches = _.filter(ctrl.availableSecurityGroups, {id: securityGroup});
            if (matches.length) {
              existingNames.push(matches[0].name);
            } else {
              if (!defaultSecurityGroups.includes(securityGroup)) {
                ctrl.viewState.removedSecurityGroups.push(securityGroup);
              }
            }
          } else {
            existingNames.push(securityGroup);
          }
        });
        ctrl.loadBalancerCommand.securityGroups = _.uniq(existingNames);
        if (ctrl.viewState.removedSecurityGroups.length) {
          v2modalWizardService.markDirty('Security Groups');
        }
      } else {
        clearSecurityGroups();
      }
    }

    function updateLoadBalancerNames() {
      var account = ctrl.loadBalancerCommand.credentials,
          region = ctrl.loadBalancerCommand.region;

      const accountLoadBalancersByRegion = {};
      application.getDataSource('loadBalancers').refresh(true).then(() => {
        application.getDataSource('loadBalancers').data.forEach((loadBalancer) => {
          if (loadBalancer.account === account) {
            accountLoadBalancersByRegion[loadBalancer.region] = accountLoadBalancersByRegion[loadBalancer.region] || [];
            accountLoadBalancersByRegion[loadBalancer.region].push(loadBalancer.name);
          }
        });

        ctrl.existingLoadBalancerNames = accountLoadBalancersByRegion[region] || [];
      });
    }

    function getAvailableSubnets() {
      var account = ctrl.loadBalancerCommand.credentials,
          region = ctrl.loadBalancerCommand.region;
      return subnetReader.listSubnets().then(function(subnets) {
        return _.chain(subnets)
          .filter({account: account, region: region})
          .reject({'target': 'ec2'})
          .value();
      });
    }

    function updateAvailabilityZones() {
      var selected = ctrl.regions ?
        ctrl.regions.filter(function(region) { return region.name === ctrl.loadBalancerCommand.region; }) :
        [];
      if (selected.length) {
        ctrl.loadBalancerCommand.regionZones = angular.copy(selected[0].availabilityZones);
        ctrl.availabilityZones = selected[0].availabilityZones;
      } else {
        ctrl.availabilityZones = [];
      }
    }

    function updateSubnets() {
      getAvailableSubnets().then(function(subnets) {
        var subnetOptions = subnets.reduce(function(accumulator, subnet) {
          if (!accumulator[subnet.purpose]) {
            accumulator[subnet.purpose] = { purpose: subnet.purpose, label: subnet.label, deprecated: subnet.deprecated, vpcIds: [], availabilityZones: [] };
          }
          let acc = accumulator[subnet.purpose];
          if (acc.vpcIds.indexOf(subnet.vpcId) === -1) {
            acc.vpcIds.push(subnet.vpcId);
          }
          acc.availabilityZones.push(subnet.availabilityZone);
          return accumulator;
        }, {});

        setSubnetTypeFromVpc(subnetOptions);

        if (_.findIndex(subnetOptions, {purpose: ctrl.loadBalancerCommand.subnetType}).length === 0) {
          ctrl.loadBalancerCommand.subnetType = '';
        }
        ctrl.subnets = _.values(subnetOptions);
        ctrl.subnetUpdated();
      });
    }

    function setSubnetTypeFromVpc(subnetOptions) {
      if (ctrl.loadBalancerCommand.vpcId) {
        var currentSelection = _.find(subnetOptions, function(option) {
          return option.vpcIds.includes(ctrl.loadBalancerCommand.vpcId);
        });
        if (currentSelection) {
          ctrl.loadBalancerCommand.subnetType = currentSelection.purpose;
        }
        delete ctrl.loadBalancerCommand.vpcId;
      }
    }

    function clearSecurityGroups() {
      ctrl.availableSecurityGroups = [];
      ctrl.existingSecurityGroupNames = [];
    }

    function certificateIdAsARN(accountId, certificateId, region, certificateType) {
      if (certificateId && (certificateId.indexOf('arn:aws:iam::') !== 0 || certificateId.indexOf('arn:aws:acm:') !== 0)) {
        // If they really want to enter the ARN...
        if (certificateType === 'iam') {
          return 'arn:aws:iam::' + accountId + ':server-certificate/' + certificateId;
        }
        if (certificateType === 'acm') {
          return 'arn:aws:acm:' + region + ':' + accountId + ':certificate/' + certificateId;
        }
      }
      return certificateId;
    }

    let formatListeners = () => {
      return accountService.getAccountDetails(ctrl.loadBalancerCommand.credentials).then((account) => {
        ctrl.loadBalancerCommand.listeners.forEach((listener) => {
          listener.sslCertificateId = certificateIdAsARN(account.accountId, listener.sslCertificateName,
            ctrl.loadBalancerCommand.region, listener.sslCertificateType || ctrl.certificateTypes[0]);
        });
      });
    };

    ctrl.certificateTypes = AWSProviderSettings.loadBalancers && AWSProviderSettings.loadBalancers.certificateTypes || ['iam', 'acm'];

    initializeController();

    // Controller API

    this.refreshSecurityGroups = function () {
      ctrl.viewState.refreshingSecurityGroups = true;
      cacheInitializer.refreshCache('securityGroups').then(function() {
        ctrl.viewState.refreshingSecurityGroups = false;
        setSecurityGroupRefreshTime();
        preloadSecurityGroups().then(function() {
          updateAvailableSecurityGroups(ctrl.loadBalancerCommand.vpcId);
        });
      });
    };

    function setSecurityGroupRefreshTime() {
      ctrl.viewState.securityGroupRefreshTime = infrastructureCaches.get('securityGroups').getStats().ageMax;
    }

    this.addItems = () => ctrl.viewState.currentItems += 25;

    this.resetCurrentItems = () => ctrl.viewState.currentItems = 25;

    this.requiresHealthCheckPath = function () {
      return ctrl.loadBalancerCommand.healthCheckProtocol && ctrl.loadBalancerCommand.healthCheckProtocol.indexOf('HTTP') === 0;
    };

    this.prependForwardSlash = (text) => {
      return text && text.indexOf('/') !== 0 ? `/${text}` : text;
    };

    this.updateName = function() {
      ctrl.loadBalancerCommand.name = this.getName();
    };

    this.getName = function() {
      var elb = ctrl.loadBalancerCommand;
      var elbName = [application.name, (elb.stack || ''), (elb.detail || '')].join('-');
      return _.trimEnd(elbName, '-');
    };

    this.accountUpdated = function() {
      accountService.getRegionsForAccount(ctrl.loadBalancerCommand.credentials).then(function(regions) {
        ctrl.regions = regions;
        clearSecurityGroups();
        ctrl.regionUpdated();
      });
    };

    this.regionUpdated = function() {
      updateAvailabilityZones();
      updateLoadBalancerNames();
      updateSubnets();
      ctrl.updateName();
    };

    ctrl.subnetUpdated = function() {
      var subnetPurpose = ctrl.loadBalancerCommand.subnetType || null,
          subnet = ctrl.subnets.find(function(test) { return test.purpose === subnetPurpose; }),
          availableVpcIds = subnet ? subnet.vpcIds : [];
        updateAvailableSecurityGroups(availableVpcIds);
      if (subnetPurpose) {
        ctrl.loadBalancerCommand.vpcId = availableVpcIds.length ? availableVpcIds[0] : null;
        if (!ctrl.viewState.hideInternalFlag && !ctrl.viewState.internalFlagToggled) {
          ctrl.loadBalancerCommand.isInternal = subnetPurpose.includes('internal');
        }
        ctrl.availabilityZones = ctrl.subnets
          .find(o => o.purpose === ctrl.loadBalancerCommand.subnetType)
          .availabilityZones
          .sort();
        v2modalWizardService.includePage('Security Groups');
      } else {
        updateAvailabilityZones();
        ctrl.loadBalancerCommand.vpcId = null;
        v2modalWizardService.excludePage('Security Groups');
      }
    };

    this.internalFlagChanged = () => {
      ctrl.viewState.internalFlagToggled = true;
    };

    this.removeListener = function(index) {
      ctrl.loadBalancerCommand.listeners.splice(index, 1);
    };

    this.addListener = function() {
      ctrl.loadBalancerCommand.listeners.push({internalProtocol: 'HTTP', externalProtocol: 'HTTP', externalPort: 80});
    };

    this.listenerProtocolChanged = (listener) => {
      if (listener.externalProtocol === 'HTTPS') {
        listener.externalPort = 443;
      }
      if (listener.externalProtocol === 'HTTP') {
        listener.externalPort = 80;
      }
    };

    this.showSslCertificateNameField = function() {
      return ctrl.loadBalancerCommand.listeners.some(function(listener) {
        return listener.externalProtocol === 'HTTPS' || listener.externalProtocol === 'SSL';
      });
    };

    this.submit = function () {
      var descriptor = isNew ? 'Create' : 'Update';

      if (ctrl.forPipelineConfig) {
        // don't submit to backend for creation. Just return the loadBalancer object
        formatListeners().then(function () {
          $uibModalInstance.close(ctrl.loadBalancerCommand);
        });
      } else {
        ctrl.taskMonitor.submit(
          function() {
            return formatListeners().then(function () {
              setAvailabilityZones(ctrl.loadBalancerCommand);
              clearSecurityGroupsIfNotInVpc(ctrl.loadBalancerCommand);
              addHealthCheckToCommand(ctrl.loadBalancerCommand);
              return loadBalancerWriter.upsertLoadBalancer(ctrl.loadBalancerCommand, application, descriptor);
            });
          }
        );
      }
    };

    let addHealthCheckToCommand = (loadBalancer) => {
      let healthCheck = null;
      const protocol = loadBalancer.healthCheckProtocol || '';
      if (protocol.startsWith('HTTP')) {
        healthCheck = `${protocol}:${loadBalancer.healthCheckPort}${loadBalancer.healthCheckPath}`;
      } else {
        healthCheck = `${protocol}:${loadBalancer.healthCheckPort}`;
      }
      loadBalancer.healthCheck = healthCheck;
    };

    let setAvailabilityZones = (loadBalancer) => {
      const availabilityZones = {};
      availabilityZones[loadBalancer.region] = loadBalancer.regionZones || [];
      loadBalancer.availabilityZones = availabilityZones;
    };

    let clearSecurityGroupsIfNotInVpc = (loadBalancer) => {
      if (!loadBalancer.vpcId && !loadBalancer.subnetType) {
        loadBalancer.securityGroups = null;
      }
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
