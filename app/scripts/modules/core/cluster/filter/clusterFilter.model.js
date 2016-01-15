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
      { model: 'status', type: 'object', filterTranslator: {Up: 'Healthy', Down: 'Unhealthy', OutOfService: 'Out of Service'}},
      { model: 'availabilityZone', param: 'zone', type: 'object', filterLabel: 'availability zone' },
      { model: 'instanceType', type: 'object', filterLabel: 'instance type'},
      { model: 'providerType', type: 'object', filterLabel: 'provider', },
      { model: 'minInstances', type: 'number', filterLabel: 'instance count (min)', },
      { model: 'maxInstances', type: 'number', filterLabel: 'instance count (max)', },
      { model: 'showAllInstances', param: 'hideInstances', displayOption: true, type: 'inverse-boolean', },
      { model: 'listInstances', displayOption: true, type: 'boolean', },
      { model: 'instanceSort', displayOption: true, type: 'sortKey', defaultValue: 'launchTime' },
    ];

    filterModelService.configureFilterModel(this, filterModelConfig);

    this.multiselectInstanceGroups = [];
    this.multiselectInstancesStream = new rx.Subject();

    this.getSelectedRegions = () => Object.keys(this.sortFilter.region || {}).filter((key) => this.sortFilter.region[key]);
    this.getSelectedAvailabilityZones = () => Object.keys(this.sortFilter.availabilityZone || {}).filter((key) => this.sortFilter.availabilityZone[key]);

    this.removeCheckedAvailabilityZoneIfRegionIsNotChecked = () => {
      this.getSelectedAvailabilityZones()
        .filter( (az) => { //select the az that need don't match a region and need to be 'unchecked'
          let regions = this.getSelectedRegions();
          return this.getSelectedRegions().length && !_.any(this.getSelectedRegions(), (region) => _.includes(az, region));
        })
        .forEach( (azKey) => {
          delete this.sortFilter.availabilityZone[azKey];
        });
    };

    this.reconcileDependentFilters = () => {
      this.removeCheckedAvailabilityZoneIfRegionIsNotChecked();
    };

    this.syncNavigation = () => {
      let instancesSelected = 0;
      this.multiselectInstanceGroups.forEach((instanceGroup) => {
        instancesSelected += instanceGroup.instanceIds.length;
      });

      if ($state.includes('**.multipleInstances') && !instancesSelected) {
        $state.go('^');
      }
      if (!$state.includes('**.multipleInstances') && instancesSelected) {
        if ($state.includes('**.clusters.*')) {
          $state.go('^.multipleInstances');
        } else {
          $state.go('.multipleInstances');
        }
      }
    };

    this.getOrCreateMultiselectInstanceGroup = (serverGroup) => {
      let serverGroupName = serverGroup.name,
          account = serverGroup.account,
          region = serverGroup.region,
          cloudProvider = serverGroup.type;
      let [result] = this.multiselectInstanceGroups.filter((instanceGroup) => {
        return instanceGroup.serverGroup === serverGroupName &&
          instanceGroup.account === account &&
          instanceGroup.region === region &&
          instanceGroup.cloudProvider === cloudProvider;
      });
      if (!result) {
        result = {
          serverGroup: serverGroupName,
          account: account,
          region: region,
          cloudProvider: cloudProvider,
          instanceIds: [],
          instances: [], // populated by details controller
          selectAll: false,
        };
        this.multiselectInstanceGroups.push(result);
      }
      return result;
    };

    this.toggleMultiselectInstance = (serverGroup, instanceId) => {
      let group = this.getOrCreateMultiselectInstanceGroup(serverGroup);
      if (group.instanceIds.indexOf(instanceId) > -1) {
        group.instanceIds.splice(group.instanceIds.indexOf(instanceId), 1);
        group.selectAll = false;
      } else {
        group.instanceIds.push(instanceId);
      }
      this.multiselectInstancesStream.onNext();
      this.syncNavigation();
    };

    this.toggleSelectAll = (serverGroup, allInstanceIds) => {
      let group = this.getOrCreateMultiselectInstanceGroup(serverGroup);
      group.selectAll = !group.selectAll;
      group.instanceIds = group.selectAll ? allInstanceIds : [];
      this.multiselectInstancesStream.onNext();
      this.syncNavigation();
    };

    this.instanceIsMultiselected = (serverGroup, instanceId) => {
      let group = this.getOrCreateMultiselectInstanceGroup(serverGroup);
      return group.instanceIds.indexOf(instanceId) > -1;
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

    function changingApplications(toParams, fromParams) {
      return toParams.application !== fromParams.application;
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
      if (movingFromClusterState(toState, fromState) || changingApplications(toParams, fromParams)) {
        this.multiselectInstanceGroups.length = 0;
        this.multiselectInstancesStream.onNext();
      }
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
