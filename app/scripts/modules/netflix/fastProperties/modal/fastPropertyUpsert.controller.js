'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.strategySelector.controller', [
    require('../fastProperty.strategy.provider.js'),
    require('../fastProperty.read.service.js'),
    require('../../../core/config/settings.js'),
    require('../../../core/application/service/applications.read.service.js'),
    require('../../../core/utils/lodash.js'),
    require('../../../core/application/listExtractor/listExtractor.service')
  ])
  .controller('FastPropertyUpsertController', function ($scope, $controller, $templateCache, $compile, $modalInstance, $q, _,
                                                        settings, applicationList, applicationReader, fastPropertyReader,
                                                        fastPropertyStrategy, clusters, appName, fastProperty, isEditing,
                                                        appListExtractorService) {
    let vm = this;

    let objectValuesToList = (object) => {
      return Object.keys(object).map((key) => object[key]);
    };

    let isSkip = (prop) => prop && prop === 'skip';

    let getDeleter = (propertyName) => {
      return function deleter() {
        if (!isSkip(vm.propertyScope[propertyName])) {
          delete vm.propertyScope[propertyName];
        }
        return vm.propertyScope;
      };
    };

    let ifNotInListDo = (list, property, fn) => {
      if(!_.includes(list, property)) {
        fn();
      }
    };

    let prepareScope = (propertyScope) => {
      vm.selectedScope = _(propertyScope)
        .omit(isSkip)
        .transform((result, value, key) => {
          if(key === 'applications') {
            result.appId = value.join(',');
          } else if (key === 'availabilityZone') {
            result.zone = value;
          } else if (key === 'instance') {
            result.serverId = value;
          } else {
             result[key] = value;
          }
          result.env = vm.property.env;
        })
        .value();

      return vm.selectedScope;
    };


    let getImpact = () => {
      let fastPropertyScope = prepareScope(vm.propertyScope);
      vm.impactLoading = true;
      fastPropertyReader.fetchImpactCountForScope(fastPropertyScope)
        .then((impact) => vm.impact = impact.count)
        .finally( () => {
          vm.impactLoading = false;
        });
    };


    //vm.property = {
    //  key: 'foo',
    //  value: 'bar',
    //  constraints: 'none',
    //  email: 'zthrash@netflix.com',
    //  cmcTicket: 'zkt'
    //};

    vm.property = angular.copy(fastProperty);

    vm.chosenApps = {};

    vm.applicationList = [];
    vm.stackList = [];
    vm.clusterList = [];
    vm.asgList = [];
    vm.zoneList = [];
    vm.instanceList = [];

    vm.isEditing = isEditing || false;
    vm.heading = vm.isEditing ? 'Update Fast Property' : 'Create Fast Property';
    vm.clusters = clusters;
    vm.appName = appName;
    vm.propertyScope = angular.copy(fastProperty.selectedScope)|| {};

    vm.strategies = fastPropertyStrategy.getStrategies();
    vm.selectedStrategy = vm.strategies.length === 1 ? {selected: _.first(vm.strategies)} : {};

    vm.freeFormClusterField = false;
    vm.freeFormAsgField = false;

    vm.toggleFreeFormClusterField = function() {
      vm.freeFormClusterField= !vm.freeFormClusterField;
    };

    vm.toggleFreeFormAsgField = function() {
      vm.freeFormAsgField = !vm.freeFormAsgField;
    };


    let clusterFilter = (cluster) => {
      return isSkip(vm.propertyScope.cluster) || cluster.name === vm.propertyScope.cluster;
    };

    let serverGroupFilter =  (serverGroup) => {
      return isSkip(vm.propertyScope.asg) || serverGroup.name === vm.propertyScope.asg;
    };

    let availabilityZoneFilter = (instance) => {
      return isSkip(vm.propertyScope.availabilityZone) || instance.availabilityZone === vm.propertyScope.availabilityZone;
    };

    let regionFilter = (serverGroup) => {
      return isSkip(vm.propertyScope.region) || serverGroup.region === vm.propertyScope.region;
    };

    let clusterHasStackFilter = (cluster) => {
      return isSkip(vm.propertyScope.stack) || _.some(cluster.serverGroups, {stack: vm.propertyScope.stack});
    };

    vm.applicationChange = () => {
      vm.getRegions();
      if (_.isEmpty(vm.propertyScope.applications)){
        delete vm.propertyScope.applications;
      }
    };

    vm.getRegions = () => {
      if(vm.chosenApps) {
        const valueList = objectValuesToList(vm.chosenApps);
        vm.regionList = appListExtractorService.getRegions(valueList);
      } else {
        let preferredZoneList = settings.providers.aws.preferredZonesByAccount[vm.property.env];
        vm.regionList = preferredZoneList ? Object.keys(preferredZoneList) : [];
      }

      let deleteRegion = getDeleter('region');
      ifNotInListDo(vm.regionList, vm.propertyScope.region, deleteRegion);

      vm.regionChange();
    };

    vm.regionChange = () => {
      const valueList = objectValuesToList(vm.chosenApps);
      vm.stackList = appListExtractorService.getStacks(valueList, regionFilter);

      let deleteStack = getDeleter('stack');
      ifNotInListDo(vm.clusterList, vm.propertyScope.stack, deleteStack);

      vm.stackChange();
    };

    vm.stackChange = () => {
      const valueList = objectValuesToList(vm.chosenApps);
      vm.clusterList = appListExtractorService.getClusters(valueList, clusterHasStackFilter);

      let deleteCluster = getDeleter('cluster');
      ifNotInListDo(vm.clusterList, vm.propertyScope.cluster, deleteCluster);

      vm.clusterChange();
    };


    vm.clusterChange = () => {
      const valueList = objectValuesToList(vm.chosenApps);
      vm.asgList = appListExtractorService.getAsgs(valueList, clusterFilter);

      let deleteAsg = getDeleter('asg');
      ifNotInListDo(vm.asgList, vm.propertyScope.asg, deleteAsg);

      vm.asgChange();
    };


    vm.asgChange = () => {
      const valueList = objectValuesToList(vm.chosenApps);
      vm.zoneList = appListExtractorService.getZones(valueList, clusterFilter, regionFilter, serverGroupFilter);

      let deleteZone = getDeleter('availabilityZone');
      ifNotInListDo(vm.zoneList, vm.propertyScope.availabilityZone, deleteZone);

      vm.availabilityZoneChange();
    };


    vm.availabilityZoneChange = () => {
      const valueList = objectValuesToList(vm.chosenApps);

      vm.instanceList = appListExtractorService.getInstances(valueList, clusterFilter, serverGroupFilter, availabilityZoneFilter);

      let deleteInstance = getDeleter('instance');
      ifNotInListDo(_.map(vm.instanceList, 'id'), vm.propertyScope.instance, deleteInstance);

      vm.instanceChange();
    };

    vm.instanceChange = () => {
      getImpact();
    };

    vm.applicationSelected = (appName) => {
      console.log(`app selected: ${appName}`);
      applicationReader
        .getApplication(appName)
        .then( (application) => {
          vm.chosenApps[appName] = application;
          return vm.chosenApps;
        })
        .finally( () => {
          vm.applicationChange();
        });
    };


    vm.applicationRemoved = (appName) => {
      delete vm.chosenApps[appName];
      vm.applicationChange();
    };

    vm.refreshAppList = (query) => {
      //vm.applicationList = ['deck', 'foo', 'bar'];
      vm.applicationList = query ? applicationList
        .filter((app) => {
          return app.name.toLowerCase().indexOf(query.toLowerCase()) === 0;
        })
        .map((app) => {
          return app.name;
        })
        .sort() : [];
    };


    vm.setFormScope = (scope) => {
      vm.formScope = scope;
      console.log("Form Scope", vm.formScope);
    };

    vm.setStrategy = function() {
      let selected = vm.getSelected();
      if(selected) {
        let strategyScope = $scope.$new();

        let controller = selected.controller;
        let controllerAs = selected.controllerAs;

        vm.setScopeProperties(strategyScope);
        vm.setControllerToScope(strategyScope, controller, controllerAs);
        vm.setTemplate(selected.templateUrl, strategyScope);
      }
    };

    vm.setTemplate = (templateUrl, strategyScope) => {
      let template = $templateCache.get(templateUrl);
      let compiledTemplate = $compile(template)(strategyScope);
      angular.element('#strategy-template').html(compiledTemplate);
    };

    vm.setScopeProperties = (strategyScope) => {
      strategyScope.property = vm.property;
      strategyScope.impactCount = vm.impact;
      strategyScope.clusters = vm.clusters;
      strategyScope.appName = vm.appName;
      strategyScope.isEditing = vm.isEditing;
      strategyScope.selectedScope = vm.selectedScope;
      strategyScope.$modalInstance = $modalInstance;
      strategyScope.formScope = vm.formScope;
    };


    vm.getSelected = () => {
       return vm.selectedStrategy.selected ? vm.selectedStrategy.selected : {};
    };

    vm.setControllerToScope = (strategyScope, controller, controllerAs) => {
      if(controller) {
        let ctrl = $controller(controller, {$scope: strategyScope});
        vm.submit = ctrl.submit;
        vm.update = ctrl.update;
        strategyScope[controllerAs] = ctrl;
      }
    };




    ( function init() {
      vm.propertyScope.applications = [vm.appName];
      vm.applicationSelected(vm.appName);
      vm.setStrategy();
    })();

  })
  .name;
