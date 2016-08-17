'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.configure.openstack.ports', [
    require('../../transformer.js'),
    require('../../../common/validateType.directive.js'),
])
  .controller('openstackSecurityGroupRulesController', function($scope, openstackSecurityGroupTransformer, infrastructureCaches, securityGroupReader, cacheInitializer, rx) {
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

    this.addMoreItems = () => this.infiniteScroll.currentItems += 20;

    this.addRule = function() {
      $scope.securityGroup.rules.push(openstackSecurityGroupTransformer.constructNewIngressRule());
    };

    this.getSecurityGroupRefreshTime = function() {
       return infrastructureCaches.securityGroups.getStats().ageMax;
    };

    $scope.allSecurityGroupsUpdated = new rx.Subject();

    $scope.initializeSecurityGroups = function() {
       return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
         $scope.state.securityGroupsLoaded = true;
         var account = $scope.securityGroup.credentials || $scope.securityGroup.account;
         var region = $scope.securityGroup.region;

         if(account && region) {
           $scope.availableSecurityGroups = _.filter(securityGroups[account].openstack[region]);
         } else {
           $scope.availableSecurityGroups = securityGroups;
         }

         //Add CIDR at the start of avaibleSecurityGroup Collection.
         var cidrObj = {
           id: 'CIDR',
           name: 'CIDR'
         };

         if ($scope.availableSecurityGroups.unshift && $scope.availableSecurityGroups.some(g => g.id !== 'CIDR')) {
           $scope.availableSecurityGroups.unshift(cidrObj);
         }

         $scope.allSecurityGroupsUpdated.onNext();
       });
    };

    $scope.remoteSecurityGroupSelected = function(indx, remoteSecurityGroupId) {
      var rule = $scope.securityGroup.rules[indx];
      if(remoteSecurityGroupId === 'CIDR') {
        if(rule.prevcidr === '') {
          rule.cidr = '0.0.0.0/0';
          rule.prevcidr = '0.0.0.0/0';
        }
        else  {
          rule.cidr = rule.prevcidr;
        }
      }
      else  {
        rule.prevcidr = rule.cidr;
        rule.cidr = '';
      }
    };

    this.refreshSecurityGroups = function() {
       $scope.state.refreshingSecurityGroups = true;
       return cacheInitializer.refreshCache('securityGroups').then(function() {
         return $scope.initializeSecurityGroups().then(function() {
           $scope.state.refreshingSecurityGroups = false;
         }, function() {
           $scope.state.refreshingSecurityGroups = false;
         });
       }, function() {
         $scope.state.refreshingSecurityGroups = false;
       });
    };

    this.removeRule = function(i) {
      $scope.securityGroup.rules.splice(i, 1);
    };

    $scope.loadSecurityGroups = function(account, region) {
      if(account !== undefined && region !== undefined)  {
        $scope.initializeSecurityGroups();
      }
    };
  });
