'use strict';

import _ from 'lodash';
let angular = require('angular');

import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.propertyStage', [
  AUTHENTICATION_SERVICE,
  require('core/config/settings.js'),
  require('netflix/fastProperties/modal/wizard/scope/index'),
  require('netflix/fastProperties/modal/fastPropertyScopeBuilder.service.js'),
  require('netflix/fastProperties/fastProperty.read.service.js')
])
  .config(function (pipelineConfigProvider, settings) {
    if (settings.feature && settings.feature.netflixMode) {
      pipelineConfigProvider.registerStage({
        label: 'Persisted Properties',
        description: 'Deploy persisted properties',
        key: 'createProperty',
        templateUrl: require('./propertyStage.html'),
        executionDetailsUrl: require('./propertyExecutionDetails.html'),
        executionSummaryUrl: require('./propertyExecutionSummary.html'),
        executionLabelTemplateUrl: require('./propertyExecutionLabel.html'),
        controller: 'PropertyStageCtrl',
        controllerAs: 'propertyStage',
        validators: [
          { type: 'requiredField', fieldName: 'email' },
        ],
      });
    }
  })
  .controller('PropertyStageCtrl', function ($scope, $uibModal, stage, authenticationService,
                                             namingService, providerSelectionService, fastPropertyReader,
                                             awsServerGroupTransformer, accountService,
                                             fastPropertyScopeBuilderService) {

    let applicationList = [];
    let vm = this;

    let getImpact = () => {
      vm.impactLoading = true;
      Object.assign(vm.stage.scope, {asg: ''});
      fastPropertyScopeBuilderService.getImpact(vm.stage.scope, vm.stage.scope.env)
        .then((impactCount) => {
          vm.impact = impactCount;
        })
        .catch(() => {
          vm.impact = 'Unknown';
        })
        .finally(() => {
          vm.impactLoading = false;
          vm.applicationsLoaded = true;
        });
    };


    vm.applicationList = [];
    vm.chosenApps = {};

    vm.stage = stage;
    vm.stage.scope = vm.stage.scope || {};
    vm.stage.scope.env = vm.stage.scope.env || 'prod';
    vm.stage.scope.appIdList = [$scope.application.name];
    vm.stage.email = vm.stage.email || '';
    vm.stage.cmcTicket = vm.stage.cmcTicket || authenticationService.getAuthenticatedUser().name;

    vm.scopeLists = {
      application: [],
      region: [],
      stack: [],
      asg:[],
      cluster: [],
      zone:[],
      instance: [],
    };


    let bindChangeFunctions = () => {
      //vm.asgChange = fastPropertyScopeBuilderService.createAsgChangeFn(vm, vm.stage.property.targetScope, vm.scopeLists, getImpact);
      vm.clusterChange = fastPropertyScopeBuilderService.createClusterChangeWithoutDeleteFn(vm, vm.stage.scope, vm.scopeLists, getImpact);
      vm.stackChange = fastPropertyScopeBuilderService.createStackChangeWithoutDeleteFn(vm, vm.stage.scope, vm.scopeLists, vm.clusterChange);
      vm.regionChange = fastPropertyScopeBuilderService.createRegionChangeFn(vm, vm.stage.scope, vm.scopeLists, vm.stackChange);
      vm.getRegions = fastPropertyScopeBuilderService.createGetRegionsFn(vm, vm.stage.scope, vm.scopeLists, vm.regionChange);
      vm.applicationChange = fastPropertyScopeBuilderService.createApplicationChangeFn(vm.stage.scope, vm.getRegions);
      vm.applicationSelected = fastPropertyScopeBuilderService.createApplicationSelectedFn(vm, vm.applicationChange, true);
    };


    vm.appPropertyList = [];

    let getPropertiesForApp = (appName) => {
      fastPropertyReader.fetchForAppName(appName)
        .then((props) => {
          vm.appPropertyList = props.propertiesList;
        });
    };

    vm.refreshAppList = (query) => {
      vm.applicationList = query ? applicationList
        .filter((app) => {
          return app.name.toLowerCase().indexOf(query.toLowerCase()) === 0;
        })
        .map((app) => {
          return app.name;
        })
        .sort() : [];
    };

    let getPreviousPropertyStages = () => {
      return $scope.pipeline.stages.filter((stage) => stage.type === 'createProperty' && stage.refId !== vm.stage.refId);
    };

    vm.hasPreviousPropertyStages = () => {
      return getPreviousPropertyStages().length;
    };

    vm.getPreviousPropertyStageNames = () => {
      return getPreviousPropertyStages().map((stage) => stage.name);
    };

    vm.setStageFromPrevious = () => {
      let matchedStage = _.head(getPreviousPropertyStages().filter((stage) => stage.name === vm.stage.copiedFromName));
      if(matchedStage) {
        Object.assign(vm.stage, {property: matchedStage.property, persistedProperties: matchedStage.persistedProperties});
      }
      bindChangeFunctions();
      vm.applicationSelected(vm.stage.scope.appIdList);
    };



    vm.applicationsLoaded = false;
    bindChangeFunctions();
    getPropertiesForApp($scope.application.name);
    vm.applicationSelected(vm.stage.scope.appIdList);
  });
