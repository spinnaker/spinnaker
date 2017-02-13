'use strict';

import {isArray, findIndex, uniqWith, flatten, compact, uniq, values, isEmpty, sortBy, groupBy, debounce } from 'lodash';
import { CREATE_FAST_PROPERTY_WIZARD_CONTROLLER } from '../wizard/createFastPropertyWizard.controller';


let angular = require('angular');


module.exports = angular
  .module('spinnaker.netflix.fastProperties.controller', [
    require('angular-ui-router'),
    require('../fastProperty.read.service.js'),
    CREATE_FAST_PROPERTY_WIZARD_CONTROLLER,
  ])
  .controller('FastPropertiesController', function ($scope, $filter, $state, $urlRouter, $stateParams, $location, $uibModal, settings, app, fastPropertyReader) {

    let vm = this;
    let filterNames = ['key', 'value', 'app','env', 'region', 'stack', 'cluster'];

    vm.application = app;

    vm.isOn = settings.feature.fastProperty;
    vm.fetchingProperties = false;

    $scope.filters = {list: []};

    vm.clearFilters = () => {
      $scope.filters.list = [];
      vm.updateFilter();
    };

    $scope.createFilterTag = function(tag) {
      if (tag) {
        return {
          label: tag.label,
          value: tag.value,
          clear: () => {
            $scope.filters.list.splice(findIndex($scope.filters.list, {label: tag.label, value: tag.value}), 1);
            vm.updateFilter();
          }
        };
      }
    };

    function fetchFastPropertiesForApp() {
      fastPropertyReader.fetchForAppName(vm.application.name)
        .then( (data) => {
          var list = data.propertiesList || [];
          // vm.propertiesList = list;
          vm.searchResults = list.map((fp) => {
            fp.scope = extractFastPropertyScopeFromId(fp.propertyId);
            fp.appId = fp.appId || 'All (Global)';
            return fp;
          });
          return vm.searchResults;
        })
        .then((searchResults) => {
          vm.filterAndGroup(searchResults);
        }).catch(() => {
          vm.propertiesList = undefined;
          vm.searchError = `No results found for: ${vm.application.name}`;
        }).finally(() => {
          vm.fetchingProperties = false;
        });
    }




    /*
     * Convert the url filter params to tag objects
     */
    let paramToTagList = (label) => flatten([$stateParams[label]]).reduce((acc, val) => {
      if (!isEmpty(val)) {
        acc.push($scope.createFilterTag({label:label, value: val}));
      }
      return acc;
    }, []);

    let createTagsFromUrlParams = () => {
      return flatten(filterNames.map(paramToTagList));
    };

    $scope.filters.list = uniqWith(flatten([createTagsFromUrlParams()]), (a, b) => a.label === b.label && a.value === b.value);

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


    vm.isPropertiesListEmpty = () => isEmpty(vm.propertiesList);

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

    let filterPredicateFactory = (filterLabel) => {
      return (property) => {
        let scopeAttrList = $scope.filters.list
          .filter((filter) => filter.label === filterLabel)
          .map((filter) => filter.value);

        let item = property.scope[filterLabel] || property[filterLabel];
        return (scopeAttrList.length && item) ? normalizeForNone(scopeAttrList).includes(item) : true;
      };
    };


    let predicateList = filterNames.map(name => filterPredicateFactory(name));

    let filters = {
      showall: (propertiesList) => {
        let copy = propertiesList ? angular.copy(propertiesList) : [];
        return copy.filter(allPass([...predicateList]));
      },
      global: (propertiesList) => {
        let copy = propertiesList ? angular.copy(propertiesList) : [];
        return copy.filter(allPass([globalFilterPredicate, ...predicateList]));
      }
    };

    let groupByFn = {
      none: (propertiesList) => sortBy(angular.copy(propertiesList), (prop) => prop.key.toLowerCase()),
      app: (propertiesList) => {
        let groups = groupBy(angular.copy(propertiesList), 'appId' );
        for (let key in groups) {
          groups[key] = sortBy(groups[key], (prop) => prop.key.toLowerCase());
        }
        return groups;
      },
      property: (propertiesList) => groupBy(angular.copy(propertiesList), 'key')
    };

    vm.filterAndGroup = (propertyList) => {
      if(!isArray(propertyList)) {
        propertyList = flatten(values(propertyList));
      }
      vm.propertiesList = groupByFn[vm.groupName]( filters[vm.filterName](propertyList) );
      setStateParams();
    };

    let setStateParams = () => {
      if (!vm.application) {
        $location.search('q', vm.searchTerm);
      }
      $location.search('group', vm.groupName);
      $location.search('filter', vm.filterName);

      filterNames.forEach((name) => {
        $location.search(name, $scope.filters.list
          .filter((tag) => {
            return tag.label === name;
          }).map((tag) => {
            return tag.value;
          }));
      });
      $urlRouter.update(true);
    };

    vm.filteredResultPage = function() {
      return vm.resultPage(vm.filter(vm.applications));
    };

    vm.search = debounce(function () {

      $location.search('q', vm.searchTerm);

      if(vm.searchTerm.length) {
        vm.clearFilters();
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
        return compact(uniq(vm.searchResults.map((fp) => {
          return fp.scope.region;
        })));
      }
    };

    vm.getStacks = () => {
      if(vm.searchResults) {
        return compact(uniq(vm.searchResults.map((fp) => {
          return fp.scope.stack;
        })));
      }
    };

    vm.createFastProperty = () => {
      $uibModal.open({
        templateUrl: require('../wizard/createFastPropertyWizard.html'),
        controller:  'createFastPropertyWizardController',
        controllerAs: 'ctrl',
        size: 'lg',
        resolve: {
          title: () => 'Create New Fast Property',
          applicationName: () => vm.application ? vm.application.applicationName : 'spinnakerfp'
        }
      });
    };

    if (vm.application) {
      fetchFastPropertiesForApp();
    } else {
      vm.search();
    }

    return vm;
  });
