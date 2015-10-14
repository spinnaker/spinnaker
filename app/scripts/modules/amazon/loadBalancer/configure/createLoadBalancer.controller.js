'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.aws.create.controller', [
  require('../../../core/loadBalancer/loadBalancer.write.service.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/account/account.service.js'),
  require('../loadBalancer.transformer.js'),
  require('../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../../core/modal/wizard/modalWizard.service.js'),
  require('../../../core/task/monitor/taskMonitorService.js'),
  require('../../subnet/subnet.read.service.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('../../../core/naming/naming.service.js'),
  require('./loadBalancerAvailabilityZoneSelector.directive.js'),
  require('../../../core/region/regionSelectField.directive.js'),
  require('../../../core/account/accountSelectField.directive.js'),
  require('../../subnet/subnetSelectField.directive.js'),
])
  .controller('awsCreateLoadBalancerCtrl', function($scope, $modalInstance, $state, _,
                                                    accountService, awsLoadBalancerTransformer, securityGroupReader,
                                                    cacheInitializer, infrastructureCaches, loadBalancerReader,
                                                    modalWizardService, loadBalancerWriter, taskMonitorService,
                                                    subnetReader, namingService,
                                                    application, loadBalancer, isNew) {

    var ctrl = this;

    $scope.pages = {
      location: require('./createLoadBalancerProperties.html'),
      securityGroups: require('./securityGroups.html'),
      listeners: require('./listeners.html'),
      healthCheck: require('./healthCheck.html'),
    };

    $scope.isNew = isNew;

    $scope.state = {
      securityGroupsLoaded: false,
      accountsLoaded: false,
      loadBalancerNamesLoaded: false,
      submitting: false,
      removedSecurityGroups: [],
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      forceRefreshMessage: 'Getting your new load balancer from Amazon...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    var allSecurityGroups = {},
        allLoadBalancerNames = {};

    function initializeEditMode() {
      if ($scope.loadBalancer.vpcId) {
        preloadSecurityGroups().then(function() {
          updateAvailableSecurityGroups([$scope.loadBalancer.vpcId]);
        });
      }
    }

    function initializeCreateMode() {
      preloadSecurityGroups();
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
      if (loadBalancer) {
        $scope.loadBalancer = awsLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
        initializeEditMode();
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
        initializeLoadBalancerNames();
        initializeCreateMode();
      }
    }

    function initializeLoadBalancerNames() {
      loadBalancerReader.listLoadBalancers('aws').then(function(loadBalancers) {
        loadBalancers.forEach((loadBalancer) => {
          loadBalancer.accounts.forEach((account) => {
            var accountName = account.name;
            account.regions.forEach((region) => {
              var regionName = region.name;
              if (!allLoadBalancerNames[accountName]) {
                allLoadBalancerNames[accountName] = {};
              }
              if (!allLoadBalancerNames[accountName][regionName]) {
                allLoadBalancerNames[accountName][regionName] = [];
              }
              allLoadBalancerNames[accountName][regionName].push(loadBalancer.name);
            });
          });
        });
        updateLoadBalancerNames();
        $scope.state.loadBalancerNamesLoaded = true;
      });
    }

    function updateAvailableSecurityGroups(availableVpcIds) {
      var account = $scope.loadBalancer.credentials,
        region = $scope.loadBalancer.region;

      if (account && region && allSecurityGroups[account] && allSecurityGroups[account].aws[region]) {
        $scope.availableSecurityGroups = _.filter(allSecurityGroups[account].aws[region], function(securityGroup) {
          return availableVpcIds.indexOf(securityGroup.vpcId) !== -1;
        });
        $scope.existingSecurityGroupNames = _.collect($scope.availableSecurityGroups, 'name');
        // TODO: Move to settings
        var defaultSecurityGroups = ['nf-datacenter-vpc', 'nf-infrastructure-vpc', 'nf-datacenter', 'nf-infrastructure'];
        var existingNames = defaultSecurityGroups.filter(function(defaultName) {
          return $scope.existingSecurityGroupNames.indexOf(defaultName) !== -1;
        });
        $scope.loadBalancer.securityGroups.forEach(function(securityGroup) {
          if ($scope.existingSecurityGroupNames.indexOf(securityGroup) === -1) {
            var matches = _.filter($scope.availableSecurityGroups, {id: securityGroup});
            if (matches.length) {
              existingNames.push(matches[0].name);
            } else {
              if (defaultSecurityGroups.indexOf(securityGroup) === -1) {
                $scope.state.removedSecurityGroups.push(securityGroup);
              }
            }
          } else {
            existingNames.push(securityGroup);
          }
        });
        $scope.loadBalancer.securityGroups = _.unique(existingNames);
        if ($scope.state.removedSecurityGroups.length) {
          modalWizardService.getWizard().markDirty('Security Groups');
        }
      } else {
        clearSecurityGroups();
      }
    }

    function updateLoadBalancerNames() {
      var account = $scope.loadBalancer.credentials,
        region = $scope.loadBalancer.region;

      if (allLoadBalancerNames[account] && allLoadBalancerNames[account][region]) {
        $scope.existingLoadBalancerNames = allLoadBalancerNames[account][region];
      } else {
        $scope.existingLoadBalancerNames = [];
      }
    }

    function getAvailableSubnets() {
      var account = $scope.loadBalancer.credentials,
          region = $scope.loadBalancer.region;
      return subnetReader.listSubnets().then(function(subnets) {
        return _(subnets)
          .filter({account: account, region: region})
          .reject({'target': 'ec2'})
          .valueOf();
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
            accumulator[subnet.purpose] = { purpose: subnet.purpose, label: subnet.label, deprecated: subnet.deprecated, vpcIds: [] };
          }
          var vpcIds = accumulator[subnet.purpose].vpcIds;
          if (vpcIds.indexOf(subnet.vpcId) === -1) {
            vpcIds.push(subnet.vpcId);
          }
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
          return option.vpcIds.indexOf($scope.loadBalancer.vpcId) !== -1;
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

    initializeController();

    // Controller API

    this.refreshSecurityGroups = function () {
      $scope.state.refreshingSecurityGroups = true;
      cacheInitializer.refreshCache('securityGroups').then(function() {
        $scope.state.refreshingSecurityGroups = false;
        preloadSecurityGroups().then(function() {
          updateAvailableSecurityGroups($scope.loadBalancer.vpcId);
        });
      });
    };

    this.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };

    this.requiresHealthCheckPath = function () {
      return $scope.loadBalancer.healthCheckProtocol && $scope.loadBalancer.healthCheckProtocol.indexOf('HTTP') === 0;
    };

    this.updateName = function() {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function() {
      var elb = $scope.loadBalancer;
      var elbName = [application.name, (elb.stack || ''), (elb.detail || '')].join('-');
      return _.trimRight(elbName, "-");
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
          subnet = $scope.subnets.filter(function(test) { return test.purpose === subnetPurpose; }),
          availableVpcIds = subnet.length ? subnet[0].vpcIds : [];
        updateAvailableSecurityGroups(availableVpcIds);
      if (subnetPurpose) {
        $scope.loadBalancer.vpcId = availableVpcIds.length ? availableVpcIds[0] : null;
        modalWizardService.getWizard().includePage('Security Groups');
      } else {
        $scope.loadBalancer.vpcId = null;
        modalWizardService.getWizard().excludePage('Security Groups');
      }
    };

    this.removeListener = function(index) {
      $scope.loadBalancer.listeners.splice(index, 1);
    };

    this.addListener = function() {
      $scope.loadBalancer.listeners.push({internalProtocol: 'HTTP', externalProtocol: 'HTTP'});
    };

    this.showSslCertificateIdField = function() {
      return $scope.loadBalancer.listeners.some(function(listener) {
        return listener.externalProtocol === 'HTTPS' || listener.externalProtocol === 'SSL';
      });
    };

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      $modalInstance.close();
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
    };


    this.submit = function () {
      var descriptor = isNew ? 'Create' : 'Update';

      $scope.taskMonitor.submit(
        function() {
          return loadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor);
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  }).name;
