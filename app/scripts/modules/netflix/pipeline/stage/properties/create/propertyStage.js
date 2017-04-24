'use strict';

import _ from 'lodash';
let angular = require('angular');

import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';
import {FAST_PROPERTY_READ_SERVICE} from 'netflix/fastProperties/fastProperty.read.service';
import {NetflixSettings} from '../../../../netflix.settings';
import {PropertyExecutionLabel} from './PropertyExecutionLabel';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.propertyStage', [
  AUTHENTICATION_SERVICE,
  FAST_PROPERTY_READ_SERVICE
])
  .config(function (pipelineConfigProvider) {
    if (NetflixSettings.feature.netflixMode) {
      pipelineConfigProvider.registerStage({
        label: 'Persisted Properties',
        description: 'Deploy persisted properties',
        key: 'createProperty',
        templateUrl: require('./propertyStage.html'),
        executionDetailsUrl: require('./propertyExecutionDetails.html'),
        executionSummaryUrl: require('./propertyExecutionSummary.html'),
        executionLabelComponent: PropertyExecutionLabel,
        controller: 'PropertyStageCtrl',
        controllerAs: 'propertyStage',
        accountExtractor: (stage) => stage.context.scope.env,
        validators: [
          { type: 'requiredField', fieldName: 'email' },
        ],
      });
    }
  })
  .controller('PropertyStageCtrl', function ($scope, $uibModal, stage, authenticationService,
                                             namingService, providerSelectionService, fastPropertyReader) {

    let applicationList = [];
    let vm = this;

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

    vm.appPropertyList = [];

    let getPropertiesForApp = (appName) => {
      fastPropertyReader.fetchForAppName(appName)
        .then((props) => {
          vm.appPropertyList = props.propertiesList;
          vm.applicationsLoaded = true;
        });
    };

    vm.selectScope = (scopeOption) => {
      let selectedEnv = vm.stage.scope.env;
      vm.stage.scope = scopeOption;
      vm.stage.scope.env = selectedEnv;
      vm.stage.scope.appIdList = [vm.stage.scope.appId];
    };

    vm.resetScope = () => {
      vm.stage.scope = {env: vm.stage.scope.env};
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
      vm.applicationSelected(vm.stage.scope.appIdList);
    };


    vm.applicationsLoaded = false;
    getPropertiesForApp($scope.application.name);
  });
