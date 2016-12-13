'use strict';

import _ from 'lodash';
let angular = require('angular');
import {API_SERVICE} from 'core/api/api.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';

module.exports = angular.module('spinnaker.core.securityGroup.read.service', [
  require('../search/search.service.js'),
  NAMING_SERVICE,
  INFRASTRUCTURE_CACHE_SERVICE,
  require('./securityGroup.transformer.js'),
  require('../cloudProvider/serviceDelegate.service.js'),
  API_SERVICE
])
  .factory('securityGroupReader', function ($q, $log, searchService, namingService, API,
                                            infrastructureCaches, securityGroupTransformer, serviceDelegate) {

    function loadSecurityGroups() {
      return getAllSecurityGroups().then((groupsByAccount) => {
        let securityGroups = [];
        _.forOwn(groupsByAccount, (groupsByProvider, account) => {
          return _.forOwn(groupsByProvider, (groupsByRegion, provider) => {
            _.forOwn(groupsByRegion, (groups) => {
              groups.forEach((group) => {
                group.provider = provider;
                group.account = account;
              });
            });
            securityGroups.push({account: account, provider: provider, securityGroups: groupsByProvider[provider]});
          });
        });
        return indexSecurityGroups(securityGroups);
      });
    }

    function addStackToSecurityGroup(securityGroup) {
      var nameParts = namingService.parseSecurityGroupName(securityGroup.name);
      securityGroup.stack = nameParts.stack;
    }

    function loadSecurityGroupsByApplicationName(applicationName) {
      return searchService.search({q: applicationName, type: 'securityGroups', pageSize: 1000}).then(function(searchResults) {
        if (!searchResults || !searchResults.results) {
          $log.warn('WARNING: Gate security group endpoint appears to be down.');
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
        application.securityGroupsIndex = refreshedSecurityGroups;
        return attachSecurityGroups(application, nameBasedSecurityGroups, false);
      });
    }

    function resolve(index, container, securityGroupId) {
      return serviceDelegate.getDelegate(container.provider || container.type || container.cloudProvider, 'securityGroup.reader')
        .resolveIndexedSecurityGroup(index, container, securityGroupId);
    }

    function addNameBasedSecurityGroups(application, nameBasedSecurityGroups) {
      var notFoundCaught = false,
          securityGroups = [];
      nameBasedSecurityGroups.forEach(function(securityGroup) {
        try {
          var match = resolve(application.securityGroupsIndex, securityGroup, securityGroup.id);
          attachUsageFields(match);
          securityGroups.push(match);
        } catch(e) {
          $log.warn('could not initialize application security group:', securityGroup);
          notFoundCaught = true;
        }
      });
      return {notFoundCaught, securityGroups};
    }

    function addLoadBalancersSecurityGroups(application) {
      var notFoundCaught = false,
          securityGroups = [];
      application.loadBalancers.data.forEach(function(loadBalancer) {
        if (loadBalancer.securityGroups) {
          loadBalancer.securityGroups.forEach(function(securityGroupId) {
            try {
              var securityGroup = resolve(application.securityGroupsIndex, loadBalancer, securityGroupId);
              attachUsageFields(securityGroup);
              if (!securityGroup.usages.loadBalancers.some(lb => lb.name === loadBalancer.name)) {
                securityGroup.usages.loadBalancers.push({name: loadBalancer.name});
              }
              securityGroups.push(securityGroup);
            } catch (e) {
              $log.warn('could not attach security group to load balancer:', loadBalancer.name, securityGroupId, e);
              notFoundCaught = true;
            }
          });
        }
      });
      return {notFoundCaught, securityGroups};
    }

    function addServerGroupSecurityGroups(application) {
      var notFoundCaught = false,
          securityGroups = [];
      application.serverGroups.data.forEach(function(serverGroup) {
        if (serverGroup.securityGroups) {
          serverGroup.securityGroups.forEach(function (securityGroupId) {
            try {
              var securityGroup = resolve(application.securityGroupsIndex, serverGroup, securityGroupId);
              attachUsageFields(securityGroup);
              if (!securityGroup.usages.serverGroups.some(sg => sg.name === serverGroup.name)) {
                securityGroup.usages.serverGroups.push({
                  name: serverGroup.name,
                  isDisabled: serverGroup.isDisabled,
                  region: serverGroup.region,
                });
              }
              securityGroups.push(securityGroup);
            } catch (e) {
              $log.warn('could not attach security group to server group:', serverGroup.name, securityGroupId);
              notFoundCaught = true;
            }
          });
        }
      });
      return {notFoundCaught, securityGroups};
    }

    let getApplicationSecurityGroups = (application, nameBasedSecurityGroups) => {
      return loadSecurityGroups()
        .then(allSecurityGroups => application.securityGroupsIndex = allSecurityGroups)
        .then(() => $q.all([application.serverGroups.ready(), application.loadBalancers.ready()])
          .then(() => attachSecurityGroups(application, nameBasedSecurityGroups, true)));
    };

    function attachSecurityGroups(application, nameBasedSecurityGroups, retryIfNotFound) {
      let data = [];
      let notFoundCaught = false;
      if (nameBasedSecurityGroups) {
        // reset everything
        application.securityGroups.data = [];
        let nameBasedGroups = addNameBasedSecurityGroups(application, nameBasedSecurityGroups);
        notFoundCaught = nameBasedGroups.notFoundCaught;
        if (!nameBasedGroups.notFoundCaught) {
          data = nameBasedGroups.securityGroups;
        }
      } else {
        // filter down to empty (name-based only) security groups - we will repopulate usages
        data = application.securityGroups.data.filter(
          (group) => !group.usages.serverGroups.length && !group.usages.loadBalancers.length);
      }

      if (!notFoundCaught) {
        let loadBalancerSecurityGroups = addLoadBalancersSecurityGroups(application);
        notFoundCaught = loadBalancerSecurityGroups.notFoundCaught;
        if (!notFoundCaught) {
          data = data.concat(loadBalancerSecurityGroups.securityGroups.filter(sg => !data.includes(sg)));
          let serverGroupSecurityGroups = addServerGroupSecurityGroups(application);
          notFoundCaught = serverGroupSecurityGroups.notFoundCaught;
          if (!notFoundCaught) {
            data = data.concat(serverGroupSecurityGroups.securityGroups.filter(sg => !data.includes(sg)));
          }
        }
      }
      data = _.uniq(data);

      if (notFoundCaught && retryIfNotFound) {
        $log.warn('Clearing security group cache and trying again...');
        return clearCacheAndRetryAttachingSecurityGroups(application, nameBasedSecurityGroups);
      } else {
        data.forEach(addStackToSecurityGroup);

        return $q.all(data.map(securityGroupTransformer.normalizeSecurityGroup)).then(() => $q.all(data));
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

    function getSecurityGroupDetails(application, account, provider, region, vpcId, id) {
      return API.one('securityGroups').one(account).one(region).one(id).withParams({provider: provider, vpcId: vpcId}).get().then(function(details) {
        if (details && details.inboundRules) {
          details.ipRangeRules = details.inboundRules.filter(function(rule) {
            return rule.range;
          });
          details.securityGroupRules = details.inboundRules.filter(function(rule) {
            return rule.securityGroup;
          });
          details.securityGroupRules.forEach(function(inboundRule) {
            let inboundGroup = inboundRule.securityGroup;
            if (!inboundGroup.name) {
              let applicationSecurityGroup = getApplicationSecurityGroup(application, inboundGroup.accountName, details.region, inboundGroup.id);
              if (applicationSecurityGroup) {
                inboundGroup.name = applicationSecurityGroup.name;
              } else {
                inboundGroup.name = inboundGroup.id;
                inboundGroup.inferredName = true;
              }
            }
          });
        }
        return details;
      });
    }

    function getAllSecurityGroups() {
      return API.one('securityGroups').useCache(infrastructureCaches.get('securityGroups')).get();
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
      getSecurityGroupDetails: getSecurityGroupDetails,
      getApplicationSecurityGroups: getApplicationSecurityGroups,
      getApplicationSecurityGroup: getApplicationSecurityGroup,
      getAllSecurityGroups: getAllSecurityGroups
    };

  });
