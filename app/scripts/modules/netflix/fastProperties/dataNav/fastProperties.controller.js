'use strict';

let angular = require('angular');
import _ from 'lodash';

module.exports = angular
  .module('spinnaker.netflix.fastProperties.controller', [
    require('angular-ui-router'),
    require('core/application/service/applications.read.service.js'),
    require('../fastProperty.read.service.js'),
    require('core/cache/deckCacheFactory.js')
  ])
  .controller('FastPropertiesController', function ($scope, $filter, $state, $stateParams, $location, applicationReader, settings, fastPropertyReader) {
    let vm = this;
    let filterNames = ['app','env', 'region', 'stack', 'cluster'];

    vm.isOn = settings.feature.fastProperty;
    vm.fetchingProperties = false;

    $scope.filters = {list: []};

    vm.clearFilters = () => {
      $scope.filters.list = [];
      vm.updateFilter();
    };

    $scope.createFilterTag = function(tag) {
      if(tag) {
        return {
          label: tag.label,
          value: tag.value,
          clear: () => {
            $scope.filters.list.splice(_.findIndex($scope.filters.list, {label: tag.label, value: tag.value}), 1);
            vm.updateFilter();
          }
        };
      }
    };


    /*
     * Convert the url filter params to tag objects
     */
    let paramToTagList = (label) => _.flatten([$stateParams[label]]).reduce((acc, val) => {
      if (!_.isEmpty(val)) {
        acc.push($scope.createFilterTag({label:label, value: val}));
      }
      return acc;
    }, []);

    let createTagsFromUrlParams = () => {
      return _.flatten(filterNames.map(paramToTagList));
    };

    $scope.filters.list = _.uniqWith(_.flatten([createTagsFromUrlParams()]), (a, b) => a.label === b.label && a.value === b.value);

    $scope.$watchCollection('filters', () => {
      if(vm.propertiesList) {
        vm.updateFilter();
      }
    });

    vm.searchTerm = $stateParams.q || '';

    vm.filterNames = {
      SHOW_ALL: 'showall',
      GLOBAL: 'global'
    };

    vm.groupNames = {
      NONE: 'none',
      APP: 'app',
      PROPERTY: 'property'
    };


    vm.isPropertiesListEmpty = () => _.isEmpty(vm.propertiesList);

    // FILTER SETTINGS
    vm.filterName = $stateParams.filter || vm.filterNames.SHOW_ALL;
    vm.groupName = $stateParams.group || vm.groupNames.NONE;

    vm.selectedFilterIs = (filterName) => {
      return vm.filterName === filterName;
    };

    vm.setFilterTo = (filterName) => {
      vm.filterName = filterName;
      vm.filterAndGroup(vm.searchResults);
    };

    vm.setGroupTo = (groupByName) => {
      vm.groupName = groupByName;
      vm.filterAndGroup(vm.searchResults);
    };

    vm.updateFilter = () => {
      vm.filterAndGroup(vm.searchResults);
    };


    vm.selectedGroupIs = (groupByName) => {
      return vm.groupName === groupByName;
    };

    let allPass = (listOfPredicate) => {
      return (property) => listOfPredicate.every(predicate => predicate(property));
    };

    let globalFilterPredicate = (property) => property.appId.includes('All (Global)');

    let normalizeForNone = (keys) => {
      return keys.map((key) => {
        return key === 'none' ? '' : key;
      });
    };

    let scopeFilterPredicateFactory = (scopeLabel) => {
      return (property) => {
        let scopeAttrList = $scope.filters.list
          .filter((filter) => filter.label === scopeLabel)
          .map((filter) => filter.value);
        return scopeAttrList.length ? normalizeForNone(scopeAttrList).includes(property.scope[scopeLabel]) : true;
      };
    };


    let predicateList = filterNames.map(name => scopeFilterPredicateFactory(name));

    let filters = {
      showall: (propertiesList) => angular.copy(propertiesList).filter(allPass([...predicateList])),
      global: (propertiesList) => angular.copy(propertiesList).filter(allPass([globalFilterPredicate, ...predicateList])),
    };

    let groupByFn = {
      none: (propertiesList) => _.sortBy(angular.copy(propertiesList), (prop) => prop.key.toLowerCase()),
      app: (propertiesList) => {
        let groups = _.groupBy(angular.copy(propertiesList), 'appId' );
        for (let key in groups) {
          groups[key] = _.sortBy(groups[key], (prop) => prop.key.toLowerCase());
        }
        return groups;
      },
      property: (propertiesList) => _.groupBy(angular.copy(propertiesList), 'key')
    };

    vm.filterAndGroup = (propertyList) => {
      vm.propertiesList = groupByFn[vm.groupName]( filters[vm.filterName](propertyList) );
      setStateParams();
    };

    let setStateParams = () => {
      $location.search('q', vm.searchTerm);
      $location.search('group', vm.groupName);
      $location.search('filter', vm.filterName);

      filterNames.forEach((name) => {
        $location.search(name, $scope.filters.list.filter(tag => tag.label === name).map(tag => tag.value));
      });
    };

    vm.filteredResultPage = function() {
      return vm.resultPage(vm.filter(vm.applications));
    };

    vm.search = _.debounce(function () {

      $location.search('q', vm.searchTerm);

      if(vm.searchTerm.length) {
        vm.fetchingProperties = true;
        vm.propertiesList = undefined;
        vm.searchError = undefined;

        fastPropertyReader.search(vm.searchTerm).then((data) => {
          vm.searchResults = data.propertiesList.map((fp) => {
            fp.scope = extractFastPropertyScopeFromId(fp.propertyId);
            fp.appId = fp.appId || 'All (Global)';
            return fp;
          });
          return vm.searchResults;
        }).then((searchResults) => {
          vm.filterAndGroup(searchResults);
        }).catch(() => {
          vm.propertiesList = undefined;
          vm.searchError = `No results found for: ${vm.searchTerm}`;
        }).finally(() => {
          vm.fetchingProperties = false;
        });
      } else {
        vm.propertiesList = undefined;
        vm.fetchingProperties = false;
      }

    }, 500);


    let extractFastPropertyScopeFromId = (propertyId) => {
      // Property Id is a pipe delimited key of that has most of the scope info in it.
      // $NAME|$APPLICATION|$ENVIRONMENT|$REGION||$STACK|$COUNTRY(|cluster=$CLUSTER)
      if (propertyId) {
        let items = propertyId.split('|');
        return {
          key: items[0],
          app: items[1],
          env: items[2],
          region: items[3],
          stack: items[5],
          cluster: items[7] ? items[7].split('=')[1] : '',
        };
      }
      return {};
    };


    vm.toggleExtraFilters = () => {
      vm.extraFiltersOpened = !vm.extraFiltersOpened;
      setStateParams();
    };

    vm.getRegions = () => {
      if(vm.searchResults) {
        return _.compact(_.uniq(vm.searchResults.map((fp) => {
          return fp.scope.region;
        })));
      }
    };

    vm.getStacks = () => {
      if(vm.searchResults) {
        return _.compact(_.uniq(vm.searchResults.map((fp) => {
          return fp.scope.stack;
        })));
      }
    };


    vm.search();


    return vm;
  });
