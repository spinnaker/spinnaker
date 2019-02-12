'use strict';

const angular = require('angular');
import _ from 'lodash';
import { Subject } from 'rxjs';

import {
  AccountService,
  InfrastructureCaches,
  NameUtils,
  SECURITY_GROUP_READER,
  SecurityGroupWriter,
  FirewallLabels,
  TaskMonitor,
  ModalWizard,
} from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { VpcReader } from 'amazon/vpc/VpcReader';

module.exports = angular
  .module('spinnaker.amazon.securityGroup.baseConfig.controller', [
    require('@uirouter/angularjs').default,
    SECURITY_GROUP_READER,
  ])
  .controller('awsConfigSecurityGroupMixin', function(
    $scope,
    $state,
    $uibModalInstance,
    application,
    securityGroup,
    securityGroupReader,
    cacheInitializer,
  ) {
    var ctrl = this;

    $scope.state = {
      submitting: false,
      refreshingSecurityGroups: false,
      removedRules: [],
      infiniteScroll: {
        numToAdd: 20,
        currentItems: 20,
      },
    };

    $scope.allVpcs = [];
    $scope.wizard = ModalWizard;
    $scope.hideClassic = false;

    ctrl.addMoreItems = function() {
      $scope.state.infiniteScroll.currentItems += $scope.state.infiniteScroll.numToAdd;
    };

    let getAccount = () => $scope.securityGroup.accountName || $scope.securityGroup.credentials;

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();
      var newStateParams = {
        name: $scope.securityGroup.name,
        accountId: getAccount(),
        region: $scope.securityGroup.regions[0],
        vpcId: $scope.securityGroup.vpcId,
        provider: 'aws',
      };
      if (!$state.includes('**.firewallDetails')) {
        $state.go('.firewallDetails', newStateParams);
      } else {
        $state.go('^.firewallDetails', newStateParams);
      }
    }

    function onTaskComplete() {
      application.securityGroups.refresh();
      application.securityGroups.onNextRefresh($scope, onApplicationRefresh);
    }

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: `Creating your ${FirewallLabels.get('firewall')}`,
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    $scope.securityGroup = securityGroup;

    ctrl.initializeAccounts = () => {
      return AccountService.listAccounts('aws').then(function(accounts) {
        $scope.accounts = accounts;
        ctrl.accountUpdated();
      });
    };

    ctrl.upsert = function() {
      $scope.taskMonitor.submit(function() {
        return SecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Create');
      });
    };

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    ctrl.accountUpdated = function() {
      const securityGroup = $scope.securityGroup;
      // sigh.
      securityGroup.account = securityGroup.accountId = securityGroup.accountName = securityGroup.credentials;
      AccountService.getRegionsForAccount(getAccount()).then(regions => {
        $scope.regions = regions.map(region => region.name);
        clearSecurityGroups();
        ctrl.regionUpdated();
        if ($scope.state.isNew) {
          ctrl.updateName();
        }
      });
    };

    ctrl.regionUpdated = function() {
      var account = getAccount(),
        regions = $scope.securityGroup.regions || [];
      VpcReader.listVpcs().then(function(vpcs) {
        var vpcsByName = _.groupBy(vpcs.filter(vpc => vpc.account === account), 'label');
        $scope.allVpcs = vpcs;
        var available = [];
        _.forOwn(vpcsByName, function(vpcsToTest, label) {
          var foundInAllRegions = regions.every(region => {
            return vpcsToTest.some(test => test.region === region && test.account === account);
          });
          if (foundInAllRegions) {
            available.push({
              ids: vpcsToTest.filter(t => regions.includes(t.region)).map(vpc => vpc.id),
              label: label,
              deprecated: vpcsToTest[0].deprecated,
            });
          }
        });

        $scope.activeVpcs = available.filter(function(vpc) {
          return !vpc.deprecated;
        });
        $scope.deprecatedVpcs = available.filter(function(vpc) {
          return vpc.deprecated;
        });
        $scope.vpcs = available;

        ctrl.updateVpcId(available);
      });
    };

    this.updateVpcId = available => {
      let lockoutDate = AWSProviderSettings.classicLaunchLockout;
      if (!securityGroup.id && lockoutDate) {
        let createTs = Number(_.get(application, 'attributes.createTs', 0));
        if (createTs >= lockoutDate) {
          $scope.hideClassic = true;
          if (!securityGroup.vpcId && available.length) {
            let defaultMatch;
            if (AWSProviderSettings.defaults.vpc) {
              const match = available.find(vpc => vpc.label === AWSProviderSettings.defaults.vpc);
              if (match) {
                defaultMatch = match.ids[0];
              }
            }
            securityGroup.vpcId =
              defaultMatch || ($scope.activeVpcs.length ? $scope.activeVpcs[0].ids[0] : available[0].ids[0]);
          }
        }
      }

      // When cloning a security group, if a user chooses a different account to clone to, but wants to retain the same VPC in this new account, it was not possible.
      // We matched the vpc ids from one account to another but they are never the same. In order to ensure that users still retain their VPC choice, irrespective of the account, we switched to using vpc names instead of vpc ids
      const selectedVpc = $scope.allVpcs.find(vpc => vpc.id === $scope.securityGroup.vpcId);
      const match = (available || []).find(vpc => selectedVpc && selectedVpc.label === vpc.label);
      const defaultVpc = (available || []).find(vpc => AWSProviderSettings.defaults.vpc === vpc.label);
      $scope.securityGroup.vpcId = (match && match.ids[0]) || (defaultVpc && defaultVpc.ids[0]);
      this.vpcUpdated();
    };

    this.vpcUpdated = function() {
      var account = getAccount(),
        regions = $scope.securityGroup.regions;
      if (account && regions.length) {
        configureFilteredSecurityGroups();
      } else {
        clearSecurityGroups();
      }
      $scope.coordinatesChanged.next();
    };

    function configureFilteredSecurityGroups() {
      var vpcId = $scope.securityGroup.vpcId || null;
      var account = getAccount();
      var regions = $scope.securityGroup.regions || [];
      var existingSecurityGroupNames = [];
      var availableSecurityGroups = [];

      regions.forEach(function(region) {
        var regionalVpcId = null;
        if (vpcId) {
          var baseVpc = _.find($scope.allVpcs, { id: vpcId });
          regionalVpcId = _.find($scope.allVpcs, { account: account, region: region, name: baseVpc.name }).id;
        }

        var regionalGroupNames = _.get(allSecurityGroups, [account, 'aws', region].join('.'), [])
          .filter(sg => sg.vpcId === regionalVpcId)
          .map(sg => sg.name);

        existingSecurityGroupNames = _.uniq(existingSecurityGroupNames.concat(regionalGroupNames));

        if (!availableSecurityGroups.length) {
          availableSecurityGroups = existingSecurityGroupNames;
        } else {
          availableSecurityGroups = _.intersection(availableSecurityGroups, regionalGroupNames);
        }
      });

      $scope.availableSecurityGroups = availableSecurityGroups;
      $scope.existingSecurityGroupNames = existingSecurityGroupNames;
      $scope.state.securityGroupsLoaded = true;
      clearInvalidSecurityGroups();
    }

    ctrl.mixinUpsert = function(descriptor) {
      $scope.taskMonitor.submit(function() {
        return SecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor);
      });
    };

    function clearInvalidSecurityGroups() {
      var removed = $scope.state.removedRules,
        securityGroup = $scope.securityGroup;
      $scope.securityGroup.securityGroupIngress = securityGroup.securityGroupIngress.filter(rule => {
        if (
          rule.accountName &&
          rule.vpcId &&
          (rule.accountName !== securityGroup.accountName || rule.vpcId !== securityGroup.vpcId)
        ) {
          return true;
        }
        if (rule.name && !$scope.availableSecurityGroups.includes(rule.name) && !removed.includes(rule.name)) {
          removed.push(rule.name);
          return false;
        }
        return true;
      });
      if (removed.length) {
        ModalWizard.markDirty('Ingress');
      }
    }

    ctrl.refreshSecurityGroups = function() {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        return ctrl.initializeSecurityGroups().then(function() {
          ctrl.vpcUpdated();
          $scope.state.refreshingSecurityGroups = false;
          setSecurityGroupRefreshTime();
        });
      });
    };

    function setSecurityGroupRefreshTime() {
      $scope.state.refreshTime = InfrastructureCaches.get('securityGroups').getStats().ageMax;
    }

    var allSecurityGroups = {};

    $scope.allSecurityGroupsUpdated = new Subject();
    $scope.coordinatesChanged = new Subject();

    ctrl.initializeSecurityGroups = function() {
      return securityGroupReader.getAllSecurityGroups().then(function(securityGroups) {
        setSecurityGroupRefreshTime();
        allSecurityGroups = securityGroups;
        var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
        var region = $scope.securityGroup.regions[0];
        var vpcId = $scope.securityGroup.vpcId || null;

        var availableGroups;
        if (account && region) {
          availableGroups = _.filter(securityGroups[account].aws[region], { vpcId: vpcId });
        } else {
          availableGroups = securityGroups;
        }

        $scope.availableSecurityGroups = _.map(availableGroups, 'name');
        $scope.allSecurityGroups = securityGroups;
        $scope.allSecurityGroupsUpdated.next();
      });
    };

    ctrl.cancel = function() {
      $uibModalInstance.dismiss();
    };

    ctrl.getCurrentNamePattern = function() {
      return $scope.securityGroup.vpcId ? vpcPattern : classicPattern;
    };

    ctrl.updateName = function() {
      const { securityGroup } = $scope;
      const name = NameUtils.getClusterName(application.name, securityGroup.stack, securityGroup.detail);
      securityGroup.name = name;
      $scope.namePreview = name;
    };

    ctrl.namePattern = {
      test: function(name) {
        return ctrl.getCurrentNamePattern().test(name);
      },
    };

    ctrl.addRule = function(ruleset) {
      ruleset.push({
        type: 'tcp',
        startPort: 7001,
        endPort: 7001,
      });
    };

    ctrl.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    ctrl.dismissRemovedRules = function() {
      $scope.state.removedRules = [];
      ModalWizard.markClean('Ingress');
      ModalWizard.markComplete('Ingress');
    };

    var classicPattern = /^[\x20-\x7F]+$/;
    var vpcPattern = /^[a-zA-Z0-9\s._\-:/()#,@[\]+=&;{}!$*]+$/;
  });
