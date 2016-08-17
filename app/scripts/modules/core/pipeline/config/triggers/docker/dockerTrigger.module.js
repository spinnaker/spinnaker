'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.trigger.docker', [
    require('../../../../../core/utils/lodash'),
    require('../../../../../core/config/settings.js'),
    require('../../../../../docker/image/image.reader.js'),
    require('./dockerTriggerOptions.directive.js'),
  ])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Docker Registry',
      description: 'Executes the pipeline on an image update',
      key: 'docker',
      controller: 'DockerTriggerCtrl as ctrl',
      controllerAs: 'vm',
      templateUrl: require('./dockerTrigger.html'),
      popoverLabelUrl: require('./dockerPopoverLabel.html'),
      manualExecutionHandler: 'dockerTriggerExecutionHandler',
    });
  })
  .factory('dockerTriggerExecutionHandler', function ($q) {
    return {
      formatLabel: (trigger) => {
        return $q.when(`(Docker Registry) ${trigger.account}: ${trigger.repository}`);
      },
      selectorTemplate: require('./selectorTemplate.html'),
    };
  })
  .controller('DockerTriggerCtrl', function (trigger, $scope, dockerImageReader, _) {
    $scope.viewState = {
      imagesLoaded: false,
      imagesRefreshing: false,
    };

    function updateOrganizationsList() {
      if (!$scope.accountMap) {
        return;
      }
      trigger.registry = $scope.registryMap[trigger.account];
      $scope.organizations = $scope.accountMap[trigger.account] || [];
      if ($scope.organizations.indexOf(trigger.organization) < 0) {
        trigger.organization = null;
      }
      updateRepositoryList();
    }

    function updateRepositoryList() {
      if (!$scope.organizationMap) {
        return;
      }
      let key = `${trigger.account}/${trigger.organization || '' }`;
      $scope.repositories = $scope.organizationMap[key] || [];
      if ($scope.repositories.indexOf(trigger.repository) < 0) {
        trigger.repository = null;
      }
      updateTag();
    }

    function updateTag() {
      if (_.trim(trigger.tag) === '') {
        trigger.tag = null;
      }
    }

    function initializeImages() {
      dockerImageReader.findImages({ provider: 'dockerRegistry' }).then(function (images) {
        $scope.images = images;
        $scope.registryMap = images.reduce((map, image) => {
          map[image.account] = image.registry;
          return map;
        }, {});
        $scope.accountMap = images.reduce((map, image) => {
          let key = image.account;
          if (!key) {
            return map;
          }
          let all = map[key] || [];
          let parts = image.repository.split('/');
          parts.pop();
          let org = parts.join('/');
          if (all.indexOf(org) < 0) {
            map[key] = all.concat(org);
          }
          return map;
        }, {});
        $scope.accounts = Object.keys($scope.accountMap);
        $scope.organizationMap = images.reduce((map, image) => {
          if (!image.repository) {
            return map;
          }
          let parts = image.repository.split('/');
          parts.pop();
          let key = `${image.account}/${parts.join('/')}`;
          let all = map[key] || [];
          if (all.indexOf(image.repository) < 0) {
            map[key] = all.concat(image.repository);
          }
          return map;
        }, {});
        $scope.organizations = Object.keys($scope.organizationMap);
        updateOrganizationsList();

        $scope.viewState.imagesLoaded = true;
        $scope.viewState.imagesRefreshing = false;
      });
    }

    this.refreshImages = function() {
      $scope.viewState.imagesRefreshing = true;
      initializeImages();
    };

    initializeImages();

    $scope.$watch('trigger.account', updateOrganizationsList);
    $scope.$watch('trigger.organization', updateRepositoryList);
    $scope.$watch('trigger.tag', updateTag);

  });
