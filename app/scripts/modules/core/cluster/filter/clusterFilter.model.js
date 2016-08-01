'use strict';

let angular = require('angular');

module.exports = angular
  .module('cluster.filter.model', [
    require('../../filterModel/filter.model.service.js'),
    require('../../navigation/urlParser.service.js'),
    require('../../utils/rx.js'),
    require('../../utils/lodash'),
  ])
  .factory('ClusterFilterModel', function($rootScope, filterModelService, urlParser, $state, rx, _) {

    var filterModel = this;
    var mostRecentParams = null;

    var filterModelConfig = [
      { model: 'filter', param: 'q', clearValue: '', type: 'string', filterLabel: 'search', },
      { model: 'account', param: 'acct', type: 'object', },
      { model: 'region', param: 'reg', type: 'object', },
      { model: 'stack', param: 'stack', type: 'object', },
      { model: 'category', param: 'category', type: 'object', },
      { model: 'status', type: 'object', filterTranslator: {Up: 'Healthy', Down: 'Unhealthy', OutOfService: 'Out of Service'}},
      { model: 'availabilityZone', param: 'zone', type: 'object', filterLabel: 'availability zone' },
      { model: 'instanceType', type: 'object', filterLabel: 'instance type'},
      { model: 'providerType', type: 'object', filterLabel: 'provider', },
      { model: 'minInstances', type: 'number', filterLabel: 'instance count (min)', },
      { model: 'maxInstances', type: 'number', filterLabel: 'instance count (max)', },
      { model: 'showAllInstances', param: 'hideInstances', displayOption: true, type: 'inverse-boolean', },
      { model: 'listInstances', displayOption: true, type: 'boolean', },
      { model: 'instanceSort', displayOption: true, type: 'sortKey', defaultValue: 'launchTime' },
      { model: 'multiselect', displayOption: true, type: 'boolean', }
    ];

    filterModelService.configureFilterModel(this, filterModelConfig);

    let getSelectedField = (field) => {
      return () => Object.keys(this.sortFilter[field] || {}).filter((key) => this.sortFilter[field][key]);
    };

    this.getSelectedRegions = getSelectedField('region');
    this.getSelectedAvailabilityZones = getSelectedField('availabilityZone');
    this.getSelectedAccounts = getSelectedField('account');

    let removeZonesNotInsideRegions = (zones, regions) => {
      zones
        .filter( (az) => {
          return regions.length && !_.any(regions, (region) => _.includes(az, region));
        })
        .forEach( (azKey) => {
          delete this.sortFilter.availabilityZone[azKey];
        });
    };

    this.removeCheckedAvailabilityZoneIfRegionIsNotChecked = (selectedZones, selectedRegions) => {
      removeZonesNotInsideRegions(selectedZones, selectedRegions);
    };

    this.removeCheckedAvailabilityZoneIfAccountIsNotChecked = (selectedZones, regionsAvailableForAccounts) => {
      removeZonesNotInsideRegions(selectedZones, regionsAvailableForAccounts);
    };

    this.removeCheckedRegionsIfAccountIsNotChecked = (selectedRegions, regionsAvailableForAccounts) => {
      let availableRegionsHash = regionsAvailableForAccounts
        .reduce((hash, r) => { // build hash so we don't have to keep looping through array.
          hash[r] = true;
          return hash;
        }, {});

      selectedRegions
        .filter((region) => {
          return !(region in availableRegionsHash);
        })
        .forEach((region) => {
          delete this.sortFilter.region[region];
        });
    };

    this.getRegionsAvailableForAccounts = (selectedAccounts, regionsKeyedByAccount) => {
      if (selectedAccounts.length === 0) {
        return _.reduce(regionsKeyedByAccount, (regions, r) => regions.concat(r), []);
      } else {
        return _(selectedAccounts)
          .map(a => regionsKeyedByAccount[a])
          .flatten()
          .valueOf();
      }
    };

    this.reconcileDependentFilters = (regionsKeyedByAccount) => {
      let selectedAccounts = this.getSelectedAccounts();
      let selectedRegions = this.getSelectedRegions();
      let selectedZones = this.getSelectedAvailabilityZones();
      let regionsAvailableForSelectedAccounts = this.getRegionsAvailableForAccounts(selectedAccounts, regionsKeyedByAccount);

      this.removeCheckedRegionsIfAccountIsNotChecked(selectedRegions, regionsAvailableForSelectedAccounts);
      this.removeCheckedAvailabilityZoneIfAccountIsNotChecked(selectedZones, regionsAvailableForSelectedAccounts);
      this.removeCheckedAvailabilityZoneIfRegionIsNotChecked(selectedZones, selectedRegions);
    };

    function isClusterState(stateName) {
      return stateName === 'home.applications.application.insight.clusters' ||
        stateName === 'home.project.application.insight.clusters';
    }

    function isClusterStateOrChild(stateName) {
      return isClusterState(stateName) || isChildState(stateName);
    }

    function isChildState(stateName) {
      return stateName.indexOf('clusters.') > -1;
    }

    function movingToClusterState(toState) {
      return isClusterStateOrChild(toState.name);
    }

    function movingFromClusterState (toState, fromState) {
      return isClusterStateOrChild(fromState.name) && !isClusterStateOrChild(toState.name);
    }

    function fromApplicationListState(fromState) {
      return fromState.name === 'home.applications';
    }

    function shouldRouteToSavedState(toParams, fromState) {
      return filterModel.hasSavedState(toParams) && !isClusterStateOrChild(fromState.name);
    }

    // WHY??? Because, when the stateChangeStart event fires, the $location.search() will return whatever the query
    // params are on the route we are going to, so if the user is using the back button, for example, to go to the
    // Infrastructure page with a search already entered, we'll pick up whatever search was entered there, and if we
    // come back to this application's clusters view, we'll get whatever that search was.
    $rootScope.$on('$locationChangeStart', function(event, toUrl, fromUrl) {
      let [oldBase, oldQuery] = fromUrl.split('?'),
          [newBase, newQuery] = toUrl.split('?');

      if (oldBase === newBase) {
        mostRecentParams = newQuery ? urlParser.parseQueryString(newQuery) : {};
      } else {
        mostRecentParams = oldQuery ? urlParser.parseQueryString(oldQuery) : {};
      }
    });

    this.handleStateChangeStart = (event, toState, toParams, fromState, fromParams) => {
      if (movingFromClusterState(toState, fromState)) {
        this.saveState(fromState, fromParams, mostRecentParams);
      }
    };

    $rootScope.$on('$stateChangeStart', this.handleStateChangeStart);

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState) {
      if (movingToClusterState(toState) && isClusterStateOrChild(fromState.name)) {
        filterModel.applyParamsToUrl();
        return;
      }
      if (movingToClusterState(toState)) {
        if (shouldRouteToSavedState(toParams, fromState)) {
          filterModel.restoreState(toParams);
        }
        if (fromApplicationListState(fromState) && !filterModel.hasSavedState(toParams)) {
          filterModel.clearFilters();
        }
      }
    });

    filterModel.activate();

    return this;

  });
