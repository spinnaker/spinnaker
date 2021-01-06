'use strict';

import { module } from 'angular';
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
  filterObjectValues,
} from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { VpcReader } from 'amazon/vpc/VpcReader';
import UIROUTER_ANGULARJS from '@uirouter/angularjs';

export const AMAZON_SECURITYGROUP_CONFIGURE_CONFIGSECURITYGROUP_MIXIN_CONTROLLER =
  'spinnaker.amazon.securityGroup.baseConfig.controller';
export const name = AMAZON_SECURITYGROUP_CONFIGURE_CONFIGSECURITYGROUP_MIXIN_CONTROLLER; // for backwards compatibility
module(AMAZON_SECURITYGROUP_CONFIGURE_CONFIGSECURITYGROUP_MIXIN_CONTROLLER, [
  UIROUTER_ANGULARJS,
  SECURITY_GROUP_READER,
]).controller('awsConfigSecurityGroupMixin', [
  '$scope',
  '$state',
  '$uibModalInstance',
  'application',
  'securityGroup',
  'securityGroupReader',
  'cacheInitializer',
  function ($scope, $state, $uibModalInstance, application, securityGroup, securityGroupReader, cacheInitializer) {
    let allSecurityGroups;
    const ctrl = this;
    $scope.self = $scope;
    $scope.application = application;
    $scope.customComponentIsvalid = true;

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

    ctrl.addMoreItems = function () {
      $scope.state.infiniteScroll.currentItems += $scope.state.infiniteScroll.numToAdd;
    };

    const getAccount = () => $scope.securityGroup.accountName || $scope.securityGroup.credentials;

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();
      const newStateParams = {
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
      return AccountService.listAllAccounts('aws').then(function (accounts) {
        $scope.accounts = accounts.filter((a) => a.authorized !== false);
        $scope.allAccounts = accounts;
        ctrl.accountUpdated();
      });
    };

    ctrl.upsert = function () {
      $scope.taskMonitor.submit(function () {
        return SecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Create');
      });
    };

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    ctrl.accountUpdated = function () {
      const securityGroup = $scope.securityGroup;
      // sigh.
      securityGroup.account = securityGroup.accountId = securityGroup.accountName = securityGroup.credentials;
      AccountService.getRegionsForAccount(getAccount()).then((regions) => {
        $scope.regions = regions.map((region) => region.name);
        clearSecurityGroups();
        ctrl.regionUpdated();
        if ($scope.state.isNew) {
          ctrl.updateName();
        }
      });
    };

    ctrl.regionUpdated = function () {
      const account = getAccount();
      const regions = $scope.securityGroup.regions || [];
      VpcReader.listVpcs().then(function (vpcs) {
        const vpcsByName = _.groupBy(
          vpcs.filter((vpc) => vpc.account === account),
          'label',
        );
        $scope.allVpcs = vpcs;
        const available = [];
        _.forOwn(vpcsByName, function (vpcsToTest, label) {
          const foundInAllRegions = regions.every((region) => {
            return vpcsToTest.some((test) => test.region === region && test.account === account);
          });
          if (foundInAllRegions) {
            available.push({
              ids: vpcsToTest.filter((t) => regions.includes(t.region)).map((vpc) => vpc.id),
              label: label,
              deprecated: vpcsToTest[0].deprecated,
            });
          }
        });

        $scope.activeVpcs = available.filter(function (vpc) {
          return !vpc.deprecated;
        });
        $scope.deprecatedVpcs = available.filter(function (vpc) {
          return vpc.deprecated;
        });
        $scope.vpcs = available;

        ctrl.updateVpcId(available);
      });
    };

    this.updateVpcId = (available) => {
      const lockoutDate = AWSProviderSettings.classicLaunchLockout;
      if (!securityGroup.id && lockoutDate) {
        const createTs = Number(_.get(application, 'attributes.createTs', 0));
        if (createTs >= lockoutDate) {
          $scope.hideClassic = true;
          if (!securityGroup.vpcId && available.length) {
            let defaultMatch;
            if (AWSProviderSettings.defaults.vpc) {
              const match = available.find((vpc) => vpc.label === AWSProviderSettings.defaults.vpc);
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
      const selectedVpc = $scope.allVpcs.find((vpc) => vpc.id === $scope.securityGroup.vpcId);
      const match = (available || []).find((vpc) => selectedVpc && selectedVpc.label === vpc.label);
      const defaultVpc =
        (available || []).find((vpc) => AWSProviderSettings.defaults.vpc === vpc.label) || ($scope.activeVpcs || [])[0];
      $scope.securityGroup.vpcId = (match && match.ids[0]) || (defaultVpc && defaultVpc.ids[0]);
      this.vpcUpdated();
    };

    this.vpcUpdated = function () {
      const account = getAccount();
      const regions = $scope.securityGroup.regions;
      if (account && regions.length) {
        configureFilteredSecurityGroups();
      } else {
        clearSecurityGroups();
      }
      $scope.coordinatesChanged.next();
    };

    function configureFilteredSecurityGroups() {
      const vpcId = $scope.securityGroup.vpcId || null;
      const account = getAccount();
      const regions = $scope.securityGroup.regions || [];
      let existingSecurityGroupNames = [];
      let availableSecurityGroups = [];

      regions.forEach(function (region) {
        let regionalVpcId = null;
        if (vpcId) {
          const baseVpc = _.find($scope.allVpcs, { id: vpcId });
          regionalVpcId = _.find($scope.allVpcs, { account: account, region: region, name: baseVpc.name }).id;
        }

        const regionalGroupNames = _.get(allSecurityGroups, [account, 'aws', region].join('.'), [])
          .filter((sg) => sg.vpcId === regionalVpcId)
          .map((sg) => sg.name);

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

    ctrl.mixinUpsert = function (descriptor) {
      $scope.taskMonitor.submit(function () {
        return SecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor);
      });
    };

    function clearInvalidSecurityGroups() {
      const removed = $scope.state.removedRules;
      const securityGroup = $scope.securityGroup;
      $scope.securityGroup.securityGroupIngress = (securityGroup.securityGroupIngress || []).filter((rule) => {
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

    ctrl.refreshSecurityGroups = function () {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function () {
        return ctrl.initializeSecurityGroups().then(function () {
          ctrl.vpcUpdated();
          $scope.state.refreshingSecurityGroups = false;
          setSecurityGroupRefreshTime();
        });
      });
    };

    function setSecurityGroupRefreshTime() {
      $scope.state.refreshTime = InfrastructureCaches.get('securityGroups').getStats().ageMax;
    }

    allSecurityGroups = {};

    $scope.allSecurityGroupsUpdated = new Subject();
    $scope.coordinatesChanged = new Subject();

    ctrl.initializeSecurityGroups = function () {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        setSecurityGroupRefreshTime();
        allSecurityGroups = securityGroups;
        const account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
        const region = $scope.securityGroup.regions[0];
        const vpcId = $scope.securityGroup.vpcId || null;

        let availableGroups;
        if (account && region) {
          availableGroups = _.filter(securityGroups[account].aws[region], { vpcId: vpcId });
        } else {
          availableGroups = securityGroups;
        }

        $scope.availableSecurityGroups = _.map(availableGroups, 'name');
        const securityGroupExclusions = AWSProviderSettings.securityGroupExclusions;
        $scope.allSecurityGroups = securityGroupExclusions
          ? filterObjectValues(securityGroups, (name) => !securityGroupExclusions.includes(name))
          : securityGroups;
        $scope.allSecurityGroupsUpdated.next();
      });
    };

    ctrl.cancel = function () {
      $uibModalInstance.dismiss();
    };

    const classicPattern = /^[\x20-\x7F]+$/;
    const vpcPattern = /^[a-zA-Z0-9\s._\-:/()#,@[\]+=&;{}!$*]+$/;

    ctrl.getCurrentNamePattern = function () {
      return $scope.securityGroup.vpcId ? vpcPattern : classicPattern;
    };

    ctrl.updateName = function () {
      const { securityGroup } = $scope;
      const appName = application.isStandalone ? application.name.split('-')[0] : application.name;
      const name = NameUtils.getClusterName(appName, securityGroup.stack, securityGroup.detail);
      securityGroup.name = name;
      $scope.namePreview = name;
    };

    ctrl.namePattern = {
      test: function (name) {
        return ctrl.getCurrentNamePattern().test(name);
      },
    };

    ctrl.addRule = function (ruleset) {
      ruleset.push({
        type: 'tcp',
        startPort: 7001,
        endPort: 7001,
      });
    };

    ctrl.removeRule = function (ruleset, index) {
      ruleset.splice(index, 1);
    };

    ctrl.updateRuleType = function (type, ruleset, index) {
      const rule = ruleset[index];
      if (type === 'icmp' || type === 'icmpv6') {
        rule.startPort = 0;
        rule.endPort = 0;
      }
    };

    ctrl.dismissRemovedRules = function () {
      $scope.state.removedRules = [];
      ModalWizard.markClean('Ingress');
      ModalWizard.markComplete('Ingress');
    };
  },
]);
