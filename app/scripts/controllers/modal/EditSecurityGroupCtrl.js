'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('EditSecurityGroupCtrl', function($scope, $modalInstance, accountService, orcaService, securityGroupService, mortService, _, applicationName, securityGroup) {

    $scope.securityGroup = securityGroup;

    securityGroup.securityGroupIngress = _(securityGroup.inboundRules)
      .filter(function(rule) {
        return rule.securityGroup;
      }).map(function(rule) {
        return rule.portRanges.map(function(portRange) {
          return {
            name: rule.securityGroup.name,
            type: rule.protocol,
            startPort: portRange.startPort,
            endPort: portRange.endPort
          };
        });
      })
      .flatten()
      .value();

    securityGroupService.getAllSecurityGroups().then(function(securityGroups) {
      var account = securityGroup.accountName,
          region = securityGroup.region,
          vpcId = securityGroup.vpcId || null;
      $scope.availableSecurityGroups = _.filter(securityGroups[account].aws[region], { vpcId: vpcId });
    });

    this.addRule = function(ruleset) {
      ruleset.push({});
    };

    this.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    this.upsert = function () {
      orcaService.upsertSecurityGroup($scope.securityGroup, applicationName)
        .then(function () {
          $modalInstance.close();
        });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
