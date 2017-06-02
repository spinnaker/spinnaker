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
import { AWSProviderSettings } from '../../aws.settings';
import { SUBNET_SELECT_FIELD_COMPONENT } from '../../subnet/subnetSelectField.component';

module.exports = angular.module('spinnaker.amazon.loadBalancer.create.controller', [
  require('@uirouter/angularjs').default,
  LOAD_BALANCER_WRITE_SERVICE,
  ACCOUNT_SERVICE,
  require('../loadBalancer.transformer.js'),
  SECURITY_GROUP_READER,
  V2_MODAL_WIZARD_SERVICE,
  TASK_MONITOR_BUILDER,
  SUBNET_READ_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  NAMING_SERVICE,
  require('./loadBalancerAvailabilityZoneSelector.directive.js'),
  SUBNET_SELECT_FIELD_COMPONENT,
])
  .controller('awsCreateLoadBalancerCtrl', function($scope, $uibModalInstance, $state,
                                                    accountService, awsLoadBalancerTransformer, securityGroupReader,
                                                    cacheInitializer, infrastructureCaches,
                                                    v2modalWizardService, loadBalancerWriter, taskMonitorBuilder,
                                                    subnetReader, namingService,
                                                    application, loadBalancer, isNew, forPipelineConfig) {

    var ctrl = this;

    $scope.pages = {
      location: require('./createLoadBalancerProperties.html'),
      securityGroups: require('./securityGroups.html'),
      listeners: require('./listeners.html'),
      healthCheck: require('./healthCheck.html'),
      advancedSettings: require('./advancedSettings.html'),
    };

    $scope.isNew = isNew;
    $scope.application = application;
    // if this controller is used in the context of "Create Load Balancer" stage,
    // then forPipelineConfig flag will be true. In that case, the Load Balancer
    // modal dialog will just return the Load Balancer object.
    $scope.forPipelineConfig = forPipelineConfig;
    $scope.submitButtonLabel = forPipelineConfig ? (isNew ? 'Add' : 'Done') : (isNew ? 'Create' : 'Update');

    $scope.state = {
      securityGroupsLoaded: false,
      accountsLoaded: false,
      submitting: false,
      removedSecurityGroups: [],
    };

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();
      var newStateParams = {
        name: $scope.loadBalancer.name,
        accountId: $scope.loadBalancer.credentials,
        region: $scope.loadBalancer.region,
        vpcId: $scope.loadBalancer.vpcId,
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

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete
    });

    var allSecurityGroups = {},
        defaultSecurityGroups = [];

    function initializeEditMode() {
      if ($scope.loadBalancer.vpcId) {
        preloadSecurityGroups().then(function() {
          updateAvailableSecurityGroups([$scope.loadBalancer.vpcId]);
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
          delete $scope.loadBalancer.isInternal;
          $scope.state.hideInternalFlag = true;
        }
      }
      accountService.listAccounts('aws').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accountsLoaded = true;
        ctrl.accountUpdated();
      });
    }

    function preloadSecurityGroups() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        allSecurityGroups = securityGroups;
        $scope.state.securityGroupsLoaded = true;
      });
    }

    function initializeController() {
      setSecurityGroupRefreshTime();
      if (loadBalancer) {
        if (forPipelineConfig) {
          $scope.loadBalancer = loadBalancer;
          initializeCreateMode();
        } else {
          $scope.loadBalancer = awsLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
          initializeEditMode();
        }
        if (isNew) {
          var nameParts = namingService.parseLoadBalancerName($scope.loadBalancer.name);
          $scope.loadBalancer.stack = nameParts.stack;
          $scope.loadBalancer.detail = nameParts.freeFormDetails;
          delete $scope.loadBalancer.name;
        }
      } else {
        $scope.loadBalancer = awsLoadBalancerTransformer.constructNewLoadBalancerTemplate(application);
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
      return $scope.loadBalancer.securityGroups.includes(a.id) ? -1 :
        $scope.loadBalancer.securityGroups.includes(b.id) ? 1 :
          0;
    }

    function updateAvailableSecurityGroups(availableVpcIds) {
      var account = $scope.loadBalancer.credentials,
        region = $scope.loadBalancer.region;

      if (account && region && allSecurityGroups[account] && allSecurityGroups[account].aws[region]) {
        $scope.availableSecurityGroups = _.filter(allSecurityGroups[account].aws[region], function(securityGroup) {
          return availableVpcIds.includes(securityGroup.vpcId);
        }).sort(availableGroupsSorter); // push existing groups to top
        $scope.existingSecurityGroupNames = _.map($scope.availableSecurityGroups, 'name');
        var existingNames = defaultSecurityGroups.filter(function(defaultName) {
          return $scope.existingSecurityGroupNames.includes(defaultName);
        });
        $scope.loadBalancer.securityGroups.forEach(function(securityGroup) {
          if (!$scope.existingSecurityGroupNames.includes(securityGroup)) {
            var matches = _.filter($scope.availableSecurityGroups, {id: securityGroup});
            if (matches.length) {
              existingNames.push(matches[0].name);
            } else {
              if (!defaultSecurityGroups.includes(securityGroup)) {
                $scope.state.removedSecurityGroups.push(securityGroup);
              }
            }
          } else {
            existingNames.push(securityGroup);
          }
        });
        $scope.loadBalancer.securityGroups = _.uniq(existingNames);
        if ($scope.state.removedSecurityGroups.length) {
          v2modalWizardService.markDirty('Security Groups');
        }
      } else {
        clearSecurityGroups();
      }
    }

    function updateLoadBalancerNames() {
      var account = $scope.loadBalancer.credentials,
          region = $scope.loadBalancer.region;

      const accountLoadBalancersByRegion = {};
      application.getDataSource('loadBalancers').refresh(true).then(() => {
        application.getDataSource('loadBalancers').data.forEach((loadBalancer) => {
          if (loadBalancer.account === account) {
            accountLoadBalancersByRegion[loadBalancer.region] = accountLoadBalancersByRegion[loadBalancer.region] || [];
            accountLoadBalancersByRegion[loadBalancer.region].push(loadBalancer.name);
          }
        });

        $scope.existingLoadBalancerNames = accountLoadBalancersByRegion[region] || [];
      });
    }

    function getAvailableSubnets() {
      var account = $scope.loadBalancer.credentials,
          region = $scope.loadBalancer.region;
      return subnetReader.listSubnets().then(function(subnets) {
        return _.chain(subnets)
          .filter({account: account, region: region})
          .reject({'target': 'ec2'})
          .value();
      });
    }

    function updateAvailabilityZones() {
      var selected = $scope.regions ?
        $scope.regions.filter(function(region) { return region.name === $scope.loadBalancer.region; }) :
        [];
      if (selected.length) {
        $scope.loadBalancer.regionZones = angular.copy(selected[0].availabilityZones);
        $scope.availabilityZones = selected[0].availabilityZones;
      } else {
        $scope.availabilityZones = [];
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

        if (_.findIndex(subnetOptions, {purpose: $scope.loadBalancer.subnetType}).length === 0) {
          $scope.loadBalancer.subnetType = '';
        }
        $scope.subnets = _.values(subnetOptions);
        ctrl.subnetUpdated();
      });
    }

    function setSubnetTypeFromVpc(subnetOptions) {
      if ($scope.loadBalancer.vpcId) {
        var currentSelection = _.find(subnetOptions, function(option) {
          return option.vpcIds.includes($scope.loadBalancer.vpcId);
        });
        if (currentSelection) {
          $scope.loadBalancer.subnetType = currentSelection.purpose;
        }
        delete $scope.loadBalancer.vpcId;
      }
    }

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
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
      return accountService.getAccountDetails($scope.loadBalancer.credentials).then((account) => {
        $scope.loadBalancer.listeners.forEach((listener) => {
          listener.sslCertificateId = certificateIdAsARN(account.accountId, listener.sslCertificateName,
            $scope.loadBalancer.region, listener.sslCertificateType || this.certificateTypes[0]);
        });
      });
    };

    this.certificateTypes = AWSProviderSettings.loadBalancers && AWSProviderSettings.loadBalancers.certificateTypes || ['iam', 'acm'];

    initializeController();

    // Controller API

    this.refreshSecurityGroups = function () {
      $scope.state.refreshingSecurityGroups = true;
      cacheInitializer.refreshCache('securityGroups').then(function() {
        $scope.state.refreshingSecurityGroups = false;
        setSecurityGroupRefreshTime();
        preloadSecurityGroups().then(function() {
          updateAvailableSecurityGroups($scope.loadBalancer.vpcId);
        });
      });
    };

    function setSecurityGroupRefreshTime() {
      ctrl.securityGroupRefreshTime = infrastructureCaches.get('securityGroups').getStats().ageMax;
    }

    this.addItems = () => this.currentItems += 25;

    this.resetCurrentItems = () => this.currentItems = 25;

    this.currentItems = 25;

    this.requiresHealthCheckPath = function () {
      return $scope.loadBalancer.healthCheckProtocol && $scope.loadBalancer.healthCheckProtocol.indexOf('HTTP') === 0;
    };

    this.prependForwardSlash = (text) => {
      return text && text.indexOf('/') !== 0 ? `/${text}` : text;
    };

    this.updateName = function() {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function() {
      var elb = $scope.loadBalancer;
      var elbName = [application.name, (elb.stack || ''), (elb.detail || '')].join('-');
      return _.trimEnd(elbName, '-');
    };

    this.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.loadBalancer.credentials).then(function(regions) {
        $scope.regions = regions;
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

    this.subnetUpdated = function() {
      var subnetPurpose = $scope.loadBalancer.subnetType || null,
          subnet = $scope.subnets.find(function(test) { return test.purpose === subnetPurpose; }),
          availableVpcIds = subnet ? subnet.vpcIds : [];
        updateAvailableSecurityGroups(availableVpcIds);
      if (subnetPurpose) {
        $scope.loadBalancer.vpcId = availableVpcIds.length ? availableVpcIds[0] : null;
        if (!$scope.state.hideInternalFlag && !$scope.state.internalFlagToggled) {
          $scope.loadBalancer.isInternal = subnetPurpose.includes('internal');
        }
        $scope.availabilityZones = $scope.subnets
          .find(o => o.purpose === $scope.loadBalancer.subnetType)
          .availabilityZones
          .sort();
        v2modalWizardService.includePage('Security Groups');
      } else {
        updateAvailabilityZones();
        $scope.loadBalancer.vpcId = null;
        v2modalWizardService.excludePage('Security Groups');
      }
    };

    this.internalFlagChanged = () => {
      $scope.state.internalFlagToggled = true;
    };

    this.removeListener = function(index) {
      $scope.loadBalancer.listeners.splice(index, 1);
    };

    this.addListener = function() {
      $scope.loadBalancer.listeners.push({internalProtocol: 'HTTP', externalProtocol: 'HTTP', externalPort: 80});
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
      return $scope.loadBalancer.listeners.some(function(listener) {
        return listener.externalProtocol === 'HTTPS' || listener.externalProtocol === 'SSL';
      });
    };

    this.submit = function () {
      var descriptor = isNew ? 'Create' : 'Update';

      if ($scope.forPipelineConfig) {
        // don't submit to backend for creation. Just return the loadBalancer object
        formatListeners().then(function () {
          $uibModalInstance.close($scope.loadBalancer);
        });
      } else {
        $scope.taskMonitor.submit(
          function() {
            return formatListeners().then(function () {
              setAvailabilityZones($scope.loadBalancer);
              clearSecurityGroupsIfNotInVpc($scope.loadBalancer);
              addHealthCheckToCommand($scope.loadBalancer);
              return loadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor);
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
