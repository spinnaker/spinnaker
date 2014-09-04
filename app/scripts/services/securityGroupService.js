'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('securityGroupService', function (settings, $q, Restangular, _, $exceptionHandler) {

    var mortEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.mortUrl + '/securityGroups');
    });

    function loadSecurityGroups(application) {

      var securityGroupPromises = [];

      application.accounts.forEach(function(account) {
        securityGroupPromises.push(mortEndpoint.one('account', account).getList());
      });

      return $q.all(securityGroupPromises).then(_.flatten);

    }

    function attachSecurityGroups(application, securityGroups) {
      var applicationSecurityGroups = [];

      var indexedSecurityGroups = indexSecurityGroups(securityGroups);

      application.securityGroupsIndex = indexedSecurityGroups;

      application.loadBalancers.forEach(function(loadBalancer) {
        if (loadBalancer.elb) {
          loadBalancer.elb.securityGroups.forEach(function(securityGroupId) {
            var securityGroup = indexedSecurityGroups[loadBalancer.account][loadBalancer.region][securityGroupId];
            if (!securityGroup.usages) {
              securityGroup.usages = { serverGroups: [], loadBalancers: [] };
            }
            securityGroup.usages.loadBalancers.push(loadBalancer);
            applicationSecurityGroups.push(securityGroup);
          });
        }
      });
      application.serverGroups.forEach(function(serverGroup) {
        serverGroup.launchConfig.securityGroups.forEach(function(securityGroupId) {
          var securityGroup = indexedSecurityGroups[serverGroup.account][serverGroup.region][securityGroupId];
          if (!securityGroup) {
            $exceptionHandler('could not find:', serverGroup.name, securityGroupId);
          } else {
            if (!securityGroup.usages) {
              securityGroup.usages = { serverGroups: [], loadBalancers: [] };
            }
            securityGroup.usages.serverGroups.push(serverGroup);
            applicationSecurityGroups.push(securityGroup);
          }
        });
      });

      application.securityGroups = _.unique(applicationSecurityGroups);
    }

    function indexSecurityGroups(securityGroups) {
      var securityGroupIndex = {};
      var securityGroupsByAccount = _.groupBy(securityGroups, 'accountName');
      _.forOwn(securityGroupsByAccount, function(securityGroup, accountName) {
        var accountIndex = securityGroupIndex[accountName] = {};
        var byRegion = _.groupBy(securityGroup, 'region');
        _.forOwn(byRegion, function(securityGroups, region) {
          accountIndex[region] = _.indexBy(securityGroups, 'id');
          _.assign(accountIndex[region], _.indexBy(securityGroups, 'name'));
        });
      });
      return securityGroupIndex;
    }

    function getSecurityGroup(application, account, region, id) {
      if (application.securityGroupsIndex[account] &&
        application.securityGroupsIndex[account][region] &&
        application.securityGroupsIndex[account][region][id]) {
        return application.securityGroupsIndex[account][region][id];
      }
      return null;
    }

    return {
      loadSecurityGroups: loadSecurityGroups,
      attachSecurityGroups: attachSecurityGroups,
      getSecurityGroup: getSecurityGroup
    };

  });
