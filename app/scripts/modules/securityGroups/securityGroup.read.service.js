'use strict';


angular.module('deckApp.securityGroup.read.service', [
  'restangular',
  'deckApp.settings',
  'deckApp.search.service',
  'deckApp.utils.lodash',
  'deckApp.caches.scheduled',
  'deckApp.caches.infrastructure',
  'deckApp.notifications.service'
])
  .factory('securityGroupReader', function ($q, $exceptionHandler, Restangular, searchService, settings, _, scheduledCache, infrastructureCaches, notificationsService) {

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
      return searchService.search('gate', {q: applicationName, type: 'securityGroups', pageSize: 1000}).then(function(searchResults) {
        if (!searchResults || !searchResults.results) {
          notificationsService.create({
            message: 'Warning: Security Group endpoint appears to be down. Security group info will not be displayed.',
            autoDismiss: false,
            hideTimestamp: true,
            strong: true
          });
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

    function attachSecurityGroups(application, securityGroups, nameBasedSecurityGroups) {
      var applicationSecurityGroups = [];

      var indexedSecurityGroups = indexSecurityGroups(securityGroups);

      application.securityGroupsIndex = indexedSecurityGroups;

      nameBasedSecurityGroups.forEach(function(securityGroup) {
        try {
          var match = indexedSecurityGroups[securityGroup.account][securityGroup.region][securityGroup.id];
          attachUsageFields(match);
          applicationSecurityGroups.push(match);
        } catch(e) {
          $exceptionHandler('could not initialize application security group:', securityGroup);
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
              $exceptionHandler('could attach security group to load balancer:', loadBalancer.name, securityGroupId);
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
              $exceptionHandler('could not attach security group to server group:', serverGroup.name, securityGroupId);
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
