'use strict';

const angular = require('angular');

import { FirewallLabels, InfrastructureCaches, SECURITY_GROUP_READER } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.securityGroup.configure.openstack.ports', [
    require('../../transformer').name,
    require('../../../common/validateType.directive').name,
    SECURITY_GROUP_READER,
  ])
  .controller('openstackSecurityGroupRulesController', [
    '$scope',
    'openstackSecurityGroupTransformer',
    'securityGroupReader',
    'cacheInitializer',
    function($scope, openstackSecurityGroupTransformer, securityGroupReader, cacheInitializer) {
      this.infiniteScroll = {
        currentItems: 20,
      };

      $scope.$watch('securityGroup.region', function() {
        var account = $scope.securityGroup.credentials || $scope.securityGroup.account;
        $scope.loadSecurityGroups(account, $scope.securityGroup.region);
      });

      $scope.$watch('securityGroup.account', function() {
        var account = $scope.securityGroup.credentials || $scope.securityGroup.account;
        $scope.loadSecurityGroups(account, $scope.securityGroup.region);
      });

      this.addMoreItems = () => (this.infiniteScroll.currentItems += 20);

      this.addRule = function() {
        $scope.securityGroup.rules.push(openstackSecurityGroupTransformer.constructNewIngressRule());
      };

      this.getSecurityGroupRefreshTime = function() {
        return InfrastructureCaches.get('securityGroups').getStats().ageMax;
      };

      $scope.initializeSecurityGroups = function() {
        return securityGroupReader.getAllSecurityGroups().then(function(securityGroups) {
          $scope.state.securityGroupsLoaded = true;
          var account = $scope.securityGroup.credentials || $scope.securityGroup.account;
          var region = $scope.securityGroup.region;

          if (account && region) {
            $scope.availableSecurityGroups = _.filter(securityGroups[account].openstack[region]);
          } else {
            $scope.availableSecurityGroups = securityGroups;
          }

          // Add self referencial option at the start of avaibleSecurityGroup Collection
          // Only do need this on create as an edit has itself in the list already.
          if ($scope.securityGroup.edit === undefined) {
            $scope.prependSecurityGroupOption({ id: 'SELF', name: `This ${FirewallLabels.get('Firewall')} (Self)` });
          }

          // Add CIDR at the start of avaibleSecurityGroup Collection
          $scope.prependSecurityGroupOption({ id: 'CIDR', name: 'CIDR' });
        });
      };

      $scope.prependSecurityGroupOption = function(option) {
        if ($scope.availableSecurityGroups.unshift && $scope.availableSecurityGroups.some(g => g.id !== option.id)) {
          $scope.availableSecurityGroups.unshift(option);
        }
      };

      $scope.remoteSecurityGroupSelected = function(indx, remoteSecurityGroupId) {
        var rule = $scope.securityGroup.rules[indx];
        if (remoteSecurityGroupId === 'CIDR') {
          if (rule.prevcidr === '') {
            rule.cidr = '0.0.0.0/0';
            rule.prevcidr = '0.0.0.0/0';
          } else {
            rule.cidr = rule.prevcidr;
          }
        } else {
          rule.prevcidr = rule.cidr;
          rule.cidr = '';
        }
      };

      this.refreshSecurityGroups = function() {
        $scope.state.refreshingSecurityGroups = true;
        return cacheInitializer.refreshCache('securityGroups').then(
          function() {
            return $scope.initializeSecurityGroups().then(
              function() {
                $scope.state.refreshingSecurityGroups = false;
              },
              function() {
                $scope.state.refreshingSecurityGroups = false;
              },
            );
          },
          function() {
            $scope.state.refreshingSecurityGroups = false;
          },
        );
      };

      this.removeRule = function(i) {
        $scope.securityGroup.rules.splice(i, 1);
      };

      $scope.loadSecurityGroups = function(account, region) {
        if (account !== undefined && region !== undefined) {
          $scope.initializeSecurityGroups();
        }
      };
    },
  ]);
