'use strict';

const angular = require('angular');
import _ from 'lodash';
import { Subject } from 'rxjs';

import { SECURITY_GROUP_FILTER_MODEL } from './securityGroupFilter.model';

module.exports = angular
  .module('securityGroup.filter.service', [
    SECURITY_GROUP_FILTER_MODEL,
    require('core/filterModel/filter.model.service'),
  ])
  .factory('securityGroupFilterService', function (securityGroupFilterModel, filterModelService) {

    var lastApplication = null;

    const groupsUpdatedStream = new Subject();

    function addSearchFields(securityGroup) {
      if (!securityGroup.searchField) {
        securityGroup.searchField = [
          securityGroup.name,
          securityGroup.id,
          securityGroup.accountName,
          securityGroup.region,
          _.map(securityGroup.usages.serverGroups, 'name').join(' '),
          _.map(securityGroup.usages.loadBalancers, 'name').join(' ')
        ].join(' ');
      }
    }

    function checkSearchTextFilter(securityGroup) {
      var filter = securityGroupFilterModel.sortFilter.filter;
      if (!filter) {
        return true;
      }

      if (filter.includes('vpc:')) {
        let [, vpcName] = /vpc:([\w-]*)/.exec(filter);
        return securityGroup.vpcName.toLowerCase() === vpcName.toLowerCase();
      }

      addSearchFields(securityGroup);
      return filter.split(' ').every(function(testWord) {
        return securityGroup.searchField.includes(testWord);
      });
    }

    function filterSecurityGroupsForDisplay(securityGroups) {
      return _.chain(securityGroups)
        .filter(checkSearchTextFilter)
        .filter(filterModelService.checkAccountFilters(securityGroupFilterModel))
        .filter(filterModelService.checkRegionFilters(securityGroupFilterModel))
        .filter(filterModelService.checkStackFilters(securityGroupFilterModel))
        .filter(filterModelService.checkProviderFilters(securityGroupFilterModel))
        .value();
    }

    /**
     * Grouping
     * @param application
     * @returns {*}
     */
    var updateSecurityGroups = _.debounce((application) => {
      if (!application) {
        application = lastApplication;
        if (!lastApplication) {
          return null;
        }
      }

      var groups = [];

      var filter = securityGroupFilterModel.sortFilter.filter.toLowerCase();
      var securityGroups = filterSecurityGroupsForDisplay(application.securityGroups.data, filter);
      var grouped = _.groupBy(securityGroups, 'account');

      _.forOwn(grouped, function(group, key) {
        var subGroupings = _.groupBy(group, 'name'),
          subGroups = [];

        _.forOwn(subGroupings, function(subGroup, subKey) {
          var subSubGroups = [];
          subGroup.forEach(function(securityGroup) {
            let heading = securityGroup.vpcName ?
              `${securityGroup.region} (${securityGroup.vpcName})` :
              securityGroup.region;
            subSubGroups.push({
              heading: heading,
              vpcName: securityGroup.vpcName,
              securityGroup: securityGroup,
            });
          });
          subGroups.push( {
            heading: subKey,
            subgroups: _.sortBy(subSubGroups, ['heading', 'vpcName']),
          });
        });

        groups.push( { heading: key, subgroups: _.sortBy(subGroups, 'heading') } );

      });

      sortGroupsByHeading(groups);
      securityGroupFilterModel.addTags();
      lastApplication = application;
      groupsUpdatedStream.next(groups);
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

    function sortGroupsByHeading(groups) {
      diffSubgroups(securityGroupFilterModel.groups, groups);

      // sort groups in place so Angular doesn't try to update the world
      securityGroupFilterModel.groups.sort(function(a, b) {
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
      securityGroupFilterModel.clearFilters();
      securityGroupFilterModel.applyParamsToUrl();
    }

    return {
      groupsUpdatedStream,
      updateSecurityGroups,
      filterSecurityGroupsForDisplay,
      sortGroupsByHeading,
      clearFilters,
    };
  }
);

