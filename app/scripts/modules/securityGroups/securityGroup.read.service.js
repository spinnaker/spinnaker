'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.read.service', [
  require('restangular'),
  require('../caches/deckCacheFactory.js'),
  require('../search/search.service.js'),
  require('../utils/lodash.js'),
  require('../caches/scheduledCache.js'),
  require('../caches/infrastructureCaches.js'),
])
  .factory('securityGroupReader', function ($q, $exceptionHandler, $log, Restangular, searchService, settings, _, scheduledCache, infrastructureCaches) {

    function loadSecurityGroups(application) {

      var securityGroupPromises = [];

      application.accounts.forEach(function(account) {
        securityGroupPromises.push(
          Restangular.all('securityGroups')
            .one(account)
            .withHttpConfig({cache: infrastructureCaches.securityGroups})
            .get()
            .then(
              function(groups) {
                return { account: account, securityGroups: groups.plain() };
              }
            )
        );
      });

      return $q.all(securityGroupPromises).then(_.flatten);

    }

    function loadSecurityGroupsByApplicationName(applicationName) {
      return searchService.search({q: applicationName, type: 'securityGroups', pageSize: 1000}).then(function(searchResults) {
        if (!searchResults || !searchResults.results) {
          $exceptionHandler('WARNING: Gate security group endpoint appears to be down.');
          return [];
        }
        return _.filter(searchResults.results, {application: applicationName});
      });
    }

    function attachUsageFields(securityGroup) {
      if (!securityGroup.usages) {
        securityGroup.usages = { serverGroups: [], loadBalancers: [] };
      }
    }

    function clearCacheAndRetryAttachingSecurityGroups(application, nameBasedSecurityGroups) {
      infrastructureCaches.clearCache('securityGroups');
      return loadSecurityGroups(application).then(function(refreshedSecurityGroups) {
        return attachSecurityGroups(application, refreshedSecurityGroups, nameBasedSecurityGroups, false);
      });
    }

    function attachSecurityGroups(application, securityGroups, nameBasedSecurityGroups, retryIfNotFound) {
      var notFoundCaught = false;
      var applicationSecurityGroups = [];

      var indexedSecurityGroups = indexSecurityGroups(securityGroups);

      application.securityGroupsIndex = indexedSecurityGroups;

      nameBasedSecurityGroups.forEach(function(securityGroup) {
        try {
          var match = indexedSecurityGroups[securityGroup.account][securityGroup.region][securityGroup.id];
          attachUsageFields(match);
          applicationSecurityGroups.push(match);
        } catch(e) {
          $log.warn('could not initialize application security group:', securityGroup);
          notFoundCaught = true;
        }
      });

      application.loadBalancers.forEach(function(loadBalancer) {
        if (loadBalancer.securityGroups) {
          loadBalancer.securityGroups.forEach(function(securityGroupId) {
            try {
              var securityGroup = indexedSecurityGroups[loadBalancer.account][loadBalancer.region][securityGroupId];
              attachUsageFields(securityGroup);
              securityGroup.usages.loadBalancers.push(loadBalancer);
              applicationSecurityGroups.push(securityGroup);
            } catch (e) {
              $log.warn('could not attach security group to load balancer:', loadBalancer.name, securityGroupId);
              notFoundCaught = true;
            }
          });
        }
      });
      application.serverGroups.forEach(function(serverGroup) {
        if (serverGroup.securityGroups) {
          serverGroup.securityGroups.forEach(function (securityGroupId) {
            try {
              var securityGroup = indexedSecurityGroups[serverGroup.account][serverGroup.region][securityGroupId];
              attachUsageFields(securityGroup);
              securityGroup.usages.serverGroups.push(serverGroup);
              applicationSecurityGroups.push(securityGroup);
            } catch (e) {
              $log.warn('could not attach security group to server group:', serverGroup.name, securityGroupId);
              notFoundCaught = true;
            }
          });
        }
      });

      if (notFoundCaught && retryIfNotFound) {
        $log.warn('Clearing security group cache and trying again...');
        return clearCacheAndRetryAttachingSecurityGroups(application, nameBasedSecurityGroups);
      } else {
        application.securityGroups = _.unique(applicationSecurityGroups);
        return $q.when(null);
      }

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

    function getSecurityGroupDetails(application, account, region, vpcId, id) {
      return Restangular.one('securityGroups', account).one(region).one(id).get({vpcId: vpcId}).then(function(details) {
        if (details && details.inboundRules) {
          details.ipRangeRules = details.inboundRules.filter(function(rule) {
            return rule.range;
          });
          details.securityGroupRules = details.inboundRules.filter(function(rule) {
            return rule.securityGroup;
          });
          details.securityGroupRules.forEach(function(inboundRule) {
            if (!inboundRule.securityGroup.name) {
              inboundRule.securityGroup.name = getApplicationSecurityGroup(application, details.accountName, details.region, inboundRule.securityGroup.id).name;
            }
          });
        }
        return details;
      });
    }

    function getAllSecurityGroups() {
      return Restangular.one('securityGroups')
        .withHttpConfig({cache: infrastructureCaches.securityGroups})
        .get();
    }

    function getApplicationSecurityGroup(application, account, region, id) {
      if (application.securityGroupsIndex[account] &&
        application.securityGroupsIndex[account][region] &&
        application.securityGroupsIndex[account][region][id]) {
        return application.securityGroupsIndex[account][region][id];
      }
      return null;
    }

    return {
      loadSecurityGroups: loadSecurityGroups,
      loadSecurityGroupsByApplicationName: loadSecurityGroupsByApplicationName,
      attachSecurityGroups: attachSecurityGroups,
      getSecurityGroupDetails: getSecurityGroupDetails,
      getApplicationSecurityGroup: getApplicationSecurityGroup,
      getAllSecurityGroups: getAllSecurityGroups
    };

  });
