'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CreateSecurityGroupCtrl', function($scope, $modalInstance, accountService, orcaService, securityGroupService, mortService, _, applicationName, securityGroup) {

    var ctrl = this;

    var allSecurityGroups = {};

    var noVpcOption = {
      id: null,
      label: 'None (Classic)'
    };

    $scope.securityGroup = securityGroup;
    $scope.vpcs = [noVpcOption];


    securityGroupService.getAllSecurityGroups().then(function(securityGroups) {
      allSecurityGroups = securityGroups;
    });

    accountService.listAccounts().then(function(accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    this.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.securityGroup.credentials).then(function(regions) {
        $scope.regions = regions;
        clearSecurityGroups();
      });
    };

    this.regionUpdated = function() {
      mortService.listVpcs().then(function(vpcs) {
        var account = $scope.securityGroup.credentials,
            region = $scope.securityGroup.region,
            availableVpcs = _(vpcs)
              .filter({ account: account, region: region })
              .map(function(vpc) {
                return {
                  id: vpc.id,
                  label: (vpc.name || '[No Name] ') + ' (' + vpc.id + ')'
                };
              })
              .value();

        $scope.vpcs = [noVpcOption].concat(availableVpcs);
        $scope.securityGroup.vpcId = null;
        ctrl.vpcUpdated();
      });
    };

    this.vpcUpdated = function() {
      var account = $scope.securityGroup.credentials,
        region = $scope.securityGroup.region,
        vpcId = $scope.securityGroup.vpcId || null;
      if (account && region && allSecurityGroups[account] && allSecurityGroups[account].aws[region]) {
        $scope.availableSecurityGroups = _.filter(allSecurityGroups[account].aws[region], { vpcId: vpcId });
        $scope.existingSecurityGroupNames = _.collect($scope.availableSecurityGroups, 'name');
      } else {
        clearSecurityGroups();
      }
    };

    var classicPattern = /^[\x00-\x7F]+$/,
      vpcPattern = /^[a-zA-Z0-9\s._\-:\/()#,@[\]+=&;{}!$*]+$/;

    this.getCurrentNamePattern = function() {
      return $scope.securityGroup.vpc ? vpcPattern : classicPattern;
    };

    this.namePattern = (function() {
      return {
        test: function(name) {
          return ctrl.getCurrentNamePattern().test(name);
        }
      };
    })();

    this.addRule = function(ruleset) {
      ruleset.push({});
    };

    this.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    this.upsert = function () {
      console.warn(orcaService);
      orcaService.upsertSecurityGroup($scope.securityGroup, applicationName)
        .then(function (response) {
          $modalInstance.close();
          console.warn('task:', response.ref);
        });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
