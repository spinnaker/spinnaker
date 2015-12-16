'use strict';

let angular = require('angular');

module.exports = angular
  .module('securityGroup.filter.service', [
    require('./securityGroup.filter.model.js'),
    require('../../utils/lodash.js'),
    require('exports?"debounce"!angular-debounce'),
    require('../../utils/waypoints/waypoint.service.js'),
    require('../../filterModel/filter.model.service.js'),
  ])
  .factory('securityGroupFilterService', function (SecurityGroupFilterModel, _, waypointService, filterModelService,
                                                  $log, debounce) {

    var lastApplication = null;

    function checkSearchTextFilter(securityGroup) {
      var filter = SecurityGroupFilterModel.sortFilter.filter;
      if (!filter) {
        return true;
      }

      if(filter.indexOf('vpc:') !== -1) {
        let [, vpcName] = /vpc:([\w-]*)/.exec(filter);
        return securityGroup.vpcName.toLowerCase() === vpcName.toLowerCase();
      }

      return filter.split(' ').every(function(testWord) {
        return securityGroup.searchField.indexOf(testWord) !== -1;
      });
    }

    function filterSecurityGroupsForDisplay(securityGroups) {
      return  _.chain(securityGroups)
        .filter(checkSearchTextFilter)
        .filter(filterModelService.checkAccountFilters(SecurityGroupFilterModel))
        .filter(filterModelService.checkRegionFilters(SecurityGroupFilterModel))
        .filter(filterModelService.checkStackFilters(SecurityGroupFilterModel))
        .filter(filterModelService.checkProviderFilters(SecurityGroupFilterModel))
        .value();
    }

    /**
     * Grouping
     * @param application
     * @returns {*}
     */
    var updateSecurityGroups = debounce((application) => {
      if (!application) {
        application = lastApplication;
        if (!lastApplication) {
          return null;
        }
      }

      var groups = [];

      var filter = SecurityGroupFilterModel.sortFilter.filter.toLowerCase();
      var securityGroups = filterSecurityGroupsForDisplay(application.securityGroups, filter);
      var grouped = _.groupBy(securityGroups, 'account');

      _.forOwn(grouped, function(group, key) {
        var subGroupings = _.groupBy(group, 'name'),
          subGroups = [];

        _.forOwn(subGroupings, function(subGroup, subKey) {
          var subSubGroups = [];
          subGroup.forEach(function(securityGroup) {
            subSubGroups.push({
              heading: securityGroup.region,
              vpcName: securityGroup.vpcName,
              securityGroup: securityGroup,
            });
          });
          subGroups.push( {
            heading: subKey,
            subgroups: _.sortBy(subSubGroups, 'heading'),
          });
        });

        groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );

      });

      sortGroupsByHeading(groups);
      waypointService.restoreToWaypoint(application.name);
      SecurityGroupFilterModel.addTags();
      lastApplication = application;
      return groups;
    }, 25);

    function diffSubgroups(oldGroups, newGroups) {
      var groupsToRemove = [];

      oldGroups.forEach(function(oldGroup, idx) {
        var newGroup = _.find(newGroups, { heading: oldGroup.heading, vpcName: oldGroup.vpcName });
        if (!newGroup) {
          groupsToRemove.push(idx);
        } else {
          if (newGroup.securityGroup) {
            oldGroup.securityGroup = newGroup.securityGroup;
          }
          if (newGroup.serverGroups) {
            diffServerGroups(oldGroup, newGroup);
          }
          if (newGroup.subgroups) {
            diffSubgroups(oldGroup.subgroups, newGroup.subgroups);
          }
        }
      });
      groupsToRemove.reverse().forEach(function(idx) {
        oldGroups.splice(idx, 1);
      });
      newGroups.forEach(function(newGroup) {
        var match = _.find(oldGroups, { heading: newGroup.heading });
        if (!match) {
          oldGroups.push(newGroup);
        }
      });
    }

    function diffServerGroups(oldGroup, newGroup) {
      var toRemove = [];
      oldGroup.serverGroups.forEach(function(serverGroup, idx) {
        var newServerGroup = _.find(newGroup.serverGroups, { name: serverGroup.name, account: serverGroup.account, region: serverGroup.region });
        if (!newServerGroup) {
          $log.debug('server group no longer found, removing:', serverGroup.name, serverGroup.account, serverGroup.region);
          toRemove.push(idx);
        }
      });
      toRemove.reverse().forEach(function(idx) {
        oldGroup.serverGroups.splice(idx, 1);
      });
      newGroup.serverGroups.forEach(function(serverGroup) {
        var oldServerGroup = _.find(oldGroup.serverGroups, { name: serverGroup.name, account: serverGroup.account, region: serverGroup.region });
        if (!oldServerGroup) {
          $log.debug('new server group found, adding', serverGroup.name, serverGroup.account, serverGroup.region);
          oldGroup.serverGroups.push(serverGroup);
        }
      });
    }

    function sortGroupsByHeading(groups) {
      diffSubgroups(SecurityGroupFilterModel.groups, groups);

      // sort groups in place so Angular doesn't try to update the world
      SecurityGroupFilterModel.groups.sort(function(a, b) {
        if (a.heading < b.heading) {
          return -1;
        }
        if (a.heading > b.heading) {
          return 1;
        }
        return 0;
      });
    }

    function clearFilters() {
      SecurityGroupFilterModel.clearFilters();
      SecurityGroupFilterModel.applyParamsToUrl();
    }

    return {
      updateSecurityGroups: updateSecurityGroups,
      filterSecurityGroupsForDisplay: filterSecurityGroupsForDisplay,
      sortGroupsByHeading: sortGroupsByHeading,
      clearFilters: clearFilters,
    };
  }
);

