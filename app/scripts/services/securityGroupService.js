'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('securityGroupService', function (settings, $q, Restangular, _, $exceptionHandler) {

    var mortEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.mortUrl);
    });

    function loadSecurityGroups(application) {

      var securityGroupPromises = [];

      application.accounts.forEach(function(account) {
        securityGroupPromises.push(mortEndpoint.all('securityGroups').one(account).get().then(function(groups) {
          return { account: account, securityGroups: groups.aws };
        }));
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
            if (!securityGroup) {
              $exceptionHandler('could not find:', loadBalancer.name, securityGroupId);
            } else {
              if (!securityGroup.usages) {
                securityGroup.usages = { serverGroups: [], loadBalancers: [] };
              }
              securityGroup.usages.loadBalancers.push(loadBalancer);
              applicationSecurityGroups.push(securityGroup);
            }
          });
        }
      });
      application.serverGroups.forEach(function(serverGroup) {
        if (serverGroup.launchConfig) {
          serverGroup.launchConfig.securityGroups.forEach(function (securityGroupId) {
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
        }
      });

      application.securityGroups = _.unique(applicationSecurityGroups);
    }

    function indexSecurityGroups(securityGroups) {
      var securityGroupIndex = {};
      securityGroups.forEach(function(group) {
        var accountName = group.account,
            accountIndex = {};
        securityGroupIndex[accountName] = accountIndex;
        _.forOwn(group.securityGroups, function(groups, region) {
          var regionIndex = {};
          accountIndex[region] = regionIndex;
          groups.forEach(function(group) {
            group.accountName = accountName;
            group.region = region;
            regionIndex[group.id] = group;
            regionIndex[group.name] = group;
          });

        });
      });
      return securityGroupIndex;
    }

    function getSecurityGroup(account, region, id) {
      return mortEndpoint.one('securityGroups', account).one('aws').one(id).get({region: region});
    }

    function getAllSecurityGroups() {
      return mortEndpoint.one('securityGroups').get();
    }

    function getSecurityGroupFromIndex(application, account, region, id) {
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
      getSecurityGroup: getSecurityGroup,
      getSecurityGroupFromIndex: getSecurityGroupFromIndex,
      getAllSecurityGroups: getAllSecurityGroups
    };

  });
