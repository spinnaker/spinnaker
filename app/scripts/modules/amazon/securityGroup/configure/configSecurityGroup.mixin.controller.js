'use strict';

import _ from 'lodash';
import {Subject} from 'rxjs';

import modalWizardServiceModule from 'core/modal/wizard/v2modalWizard.service';

var angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular
  .module('spinnaker.amazon.securityGroup.baseConfig.controller', [
    require('angular-ui-router'),
    require('core/task/monitor/taskMonitorService'),
    require('core/securityGroup/securityGroup.write.service'),
    ACCOUNT_SERVICE,
    require('../../vpc/vpc.read.service'),
    modalWizardServiceModule,
    require('core/config/settings'),
    require('./ingressRuleGroupSelector.component'),
  ])
  .controller('awsConfigSecurityGroupMixin', function ($scope,
                                                       $state,
                                                       $uibModalInstance,
                                                       taskMonitorService,
                                                       application,
                                                       securityGroup,
                                                       securityGroupReader,
                                                       securityGroupWriter,
                                                       accountService,
                                                       v2modalWizardService,
                                                       cacheInitializer,
                                                       infrastructureCaches,
                                                       vpcReader,
                                                       settings) {



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

    $scope.wizard = v2modalWizardService;

    $scope.hideClassic = false;

    ctrl.addMoreItems = function () {
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
      if (!$state.includes('**.securityGroupDetails')) {
        $state.go('.securityGroupDetails', newStateParams);
      } else {
        $state.go('^.securityGroupDetails', newStateParams);
      }
    }

    function onTaskComplete() {
      application.securityGroups.refresh();
      application.securityGroups.onNextRefresh($scope, onApplicationRefresh);
    }

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your security group',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    $scope.securityGroup = securityGroup;

    ctrl.initializeAccounts = () => {
      return accountService.listAccounts('aws').then(function (accounts) {
        $scope.accounts = accounts;
        ctrl.accountUpdated();
      });
    };

    ctrl.upsert = function () {
      $scope.taskMonitor.submit(
        function () {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Create');
        }
      );
    };

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    ctrl.accountUpdated = function () {
      accountService.getRegionsForAccount(getAccount()).then(regions => {
        $scope.regions = regions.map(region => region.name);
        clearSecurityGroups();
        ctrl.regionUpdated();
        if ($scope.state.isNew) {
          ctrl.updateName();
        }
      });
    };

    ctrl.regionUpdated = function () {
      var account = getAccount(),
        regions = $scope.securityGroup.regions || [];
      vpcReader.listVpcs().then(function (vpcs) {
        var vpcsByName = _.groupBy(vpcs.filter(vpc => vpc.account === account), 'label');
        $scope.allVpcs = vpcs;
        var available = [];
        _.forOwn(vpcsByName, function (vpcsToTest, label) {
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

        $scope.activeVpcs = available.filter(function (vpc) {
          return !vpc.deprecated;
        });
        $scope.deprecatedVpcs = available.filter(function (vpc) {
          return vpc.deprecated;
        });
        $scope.vpcs = available;

        let lockoutDate = _.get(settings, 'providers.aws.classicLaunchLockout');
        if (!securityGroup.id && lockoutDate) {
          let createTs = Number(_.get(application, 'attributes.createTs', 0));
          if (createTs >= lockoutDate) {
            $scope.hideClassic = true;
            if (!securityGroup.vpcId && available.length) {
              securityGroup.vpcId = $scope.activeVpcs.length ? $scope.activeVpcs[0].ids[0] : available[0].ids[0];
            }
          }
        }

        var match = _.find(available, function (vpc) {
          return vpc.ids.includes($scope.securityGroup.vpcId);
        });
        $scope.securityGroup.vpcId = match ? match.ids[0] : null;
        ctrl.vpcUpdated();
      });
    };

    this.vpcUpdated = function () {
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

      regions.forEach(function (region) {
        var regionalVpcId = null;
        if (vpcId) {
          var baseVpc = _.find($scope.allVpcs, {id: vpcId});
          regionalVpcId = _.find($scope.allVpcs, {account: account, region: region, name: baseVpc.name}).id;
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
      clearInvalidSecurityGroups();
    }

    ctrl.mixinUpsert = function (descriptor) {
      $scope.taskMonitor.submit(
        function () {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor);
        }
      );
    };

    function clearInvalidSecurityGroups() {
      var removed = $scope.state.removedRules,
        securityGroup = $scope.securityGroup;
      $scope.securityGroup.securityGroupIngress = securityGroup.securityGroupIngress.filter(rule => {
        if (rule.accountName !== securityGroup.accountName || (rule.vpcId && rule.vpcId !== securityGroup.vpcId)) {
          return true;
        }
        if (rule.name && !$scope.availableSecurityGroups.includes(rule.name) && !removed.includes(rule.name)) {
          removed.push(rule.name);
          return false;
        }
        return true;
      });
      if (removed.length) {
        v2modalWizardService.markDirty('Ingress');
      }
    }

    ctrl.refreshSecurityGroups = function () {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function () {
        return ctrl.initializeSecurityGroups().then(function () {
          ctrl.vpcUpdated();
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };

    this.getSecurityGroupRefreshTime = function () {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };

    var allSecurityGroups = {};

    $scope.allSecurityGroupsUpdated = new Subject();
    $scope.coordinatesChanged = new Subject();

    ctrl.initializeSecurityGroups = function () {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        $scope.state.securityGroupsLoaded = true;
        allSecurityGroups = securityGroups;
        var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
        var region = $scope.securityGroup.regions[0];
        var vpcId = $scope.securityGroup.vpcId || null;

        var availableGroups;
        if (account && region) {
          availableGroups = _.filter(securityGroups[account].aws[region], {vpcId: vpcId});
        } else {
          availableGroups = securityGroups;
        }

        $scope.availableSecurityGroups = _.map(availableGroups, 'name');
        $scope.allSecurityGroups = securityGroups;
        $scope.allSecurityGroupsUpdated.next();
      });
    };

    ctrl.cancel = function () {
      $uibModalInstance.dismiss();
    };

    ctrl.getCurrentNamePattern = function () {
      return $scope.securityGroup.vpcId ? vpcPattern : classicPattern;
    };

    ctrl.updateName = function () {
      var securityGroup = $scope.securityGroup,
        name = application.name;
      if (securityGroup.detail) {
        name += '-' + securityGroup.detail;
      }
      securityGroup.name = name;
      $scope.namePreview = name;
    };

    ctrl.namePattern = {
      test: function (name) {
        return ctrl.getCurrentNamePattern().test(name);
      }
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

    ctrl.dismissRemovedRules = function () {
      $scope.state.removedRules = [];
      v2modalWizardService.markClean('Ingress');
      v2modalWizardService.markComplete('Ingress');
    };

    var classicPattern = /^[\x00-\x7F]+$/;
    var vpcPattern = /^[a-zA-Z0-9\s._\-:\/()#,@[\]+=&;{}!$*]+$/;

  });

