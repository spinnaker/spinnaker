'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CreateLoadBalancerCtrl', function($scope, $modalInstance, application, accountService, securityGroupService, mortService, _, searchService, modalWizardService, orcaService) {
    $scope.loadBalancer = {
      credentials: null,
      vpcId: 'none',
      listeners: [
        {
          internalProtocol: 'HTTP',
          internalPort: 7001,
          externalProtocol: 'HTTP',
          externalPort: 80
        }
      ],
      healthCheckProtocol: 'HTTPS',
      healthCheckPort: 7001,
      healthCheckPath: '/health',
      regionZones: [],
      securityGroups: []
    };

    var ctrl = this;

    var allSecurityGroups = {};

    var allLoadBalancerNames = {};

    searchService.search({q: '', type: 'loadBalancers', pageSize: 100000}).then(function(response) {
      response.data[0].results.forEach(function(result) {
        if (!allLoadBalancerNames[result.account]) {
          allLoadBalancerNames[result.account] = {};
        }
        if (!allLoadBalancerNames[result.account][result.region]) {
          allLoadBalancerNames[result.account][result.region] = [];
        }
        var suffixIndex = result.loadBalancer.lastIndexOf('-frontend');
        var name = result.loadBalancer.toLowerCase();
        if (suffixIndex !== -1 && suffixIndex === name.length - 9) {
          allLoadBalancerNames[result.account][result.region].push(name.substring(0, suffixIndex));
        } else {
          allLoadBalancerNames[result.account][result.region].push(name);
        }
      });
    });

    securityGroupService.getAllSecurityGroups().then(function(securityGroups) {
      allSecurityGroups = securityGroups;
    });

    accountService.listAccounts().then(function(accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });


    function updateLoadBalancerNames() {
      var account = $scope.loadBalancer.credentials,
        region = $scope.loadBalancer.region;

      if (allLoadBalancerNames[account] && allLoadBalancerNames[account][region]) {
        $scope.usedLoadBalancerNames = allLoadBalancerNames[account][region];
      } else {
        $scope.usedLoadBalancerNames = [];
      }
    }

    function getAvailableSubnets() {
      var account = $scope.loadBalancer.credentials,
          region = $scope.loadBalancer.region;
      return mortService.listSubnets().then(function(subnets) {
        return _.filter(subnets, {account: account, region: region, target: 'elb'});
      });
    }

    function updateAvailabilityZones() {
      var selected = $scope.regions ?
        $scope.regions.filter(function(region) { return region.name === $scope.loadBalancer.region; }) :
        [];
      if (selected.length) {
        $scope.availabilityZones = selected[0].availabilityZones;
      } else {
        $scope.availabilityZones = [];
      }
    }

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

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
      getAvailableSubnets().then(function(subnets) {
        var subnetOptions = subnets.reduce(function(accumulator, subnet) {
          if (!accumulator[subnet.purpose]) {
            accumulator[subnet.purpose] = { purpose: subnet.purpose, label: subnet.purpose, vpcIds: [] };
          }
          var vpcIds = accumulator[subnet.purpose].vpcIds;
          if (vpcIds.indexOf(subnet.vpcId) === -1) {
            vpcIds.push(subnet.vpcId);
          }
          return accumulator;
        }, {});
        if (_.findIndex(subnetOptions, {purpose: $scope.loadBalancer.subnet}).length === 0) {
          $scope.loadBalancer.subnet = '';
        }
        $scope.subnets = _.values(subnetOptions);
        ctrl.subnetUpdated();
      });
    };

    this.subnetUpdated = function() {
      var account = $scope.loadBalancer.credentials,
        region = $scope.loadBalancer.region,
        subnetPurpose = $scope.loadBalancer.subnet || null,
        subnet = $scope.subnets.filter(function(test) { return test.purpose === subnetPurpose; }),
        availableVpcIds = subnet.length ? subnet[0].vpcIds : [];
      if (account && region && allSecurityGroups[account] && allSecurityGroups[account].aws[region]) {
        $scope.availableSecurityGroups = _.filter(allSecurityGroups[account].aws[region], function(securityGroup) {
          return availableVpcIds.indexOf(securityGroup.vpcId) !== -1;
        });
        $scope.existingSecurityGroupNames = _.collect($scope.availableSecurityGroups, 'name');
      } else {
        clearSecurityGroups();
      }
      if (subnetPurpose) {
        modalWizardService.getWizard().includePage('Security Groups');
      } else {
        modalWizardService.getWizard().excludePage('Security Groups');
      }
    };

    this.removeListener = function(index) {
      $scope.loadBalancer.listeners.splice(index, 1);
    };

    this.addListener = function() {
      $scope.loadBalancer.listeners.push({});
    };

    this.addSecurityGroup = function() {
      $scope.loadBalancer.securityGroups.push({});
    };

    this.removeSecurityGroup = function(index) {
      $scope.loadBalancer.securityGroups.splice(index, 1);
    };

    this.submit = function () {
      orcaService.upsertLoadBalancer($scope.loadBalancer, application.name).then(function(response) {
        $modalInstance.close();
        console.warn('task:', response.ref);
      });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
