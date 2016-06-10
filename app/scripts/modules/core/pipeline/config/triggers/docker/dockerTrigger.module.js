'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.trigger.docker', [
    require('../../../../../core/config/settings.js'),
  ])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'Docker',
      description: 'Executes the pipeline on an image update',
      key: 'docker',
      controller: 'DockerTriggerCtrl as ctrl',
      controllerAs: 'vm',
      templateUrl: require('./dockerTrigger.html'),
      popoverLabelUrl: require('./dockerPopoverLabel.html'),
    });
  })
  .controller('DockerTriggerCtrl', function (trigger, $scope, Restangular) {
    $scope.viewState = {
      imagesLoaded: false,
      imagesRefreshing: false,
    };

    function loadImages() {
      return Restangular.all('images/find').getList({ provider: 'dockerRegistry' }, {}).then(function(results) {
          return results;
        },
        function() {
          return [];
        });
    }

    function updateRepositoryList() {
      if (!$scope.registryMap) {
        return;
      }
      $scope.repositories = $scope.registryMap[trigger.registry] || [];
      if ($scope.repositories.indexOf(trigger.repository) < 0) {
        trigger.repository = null;
      }
      updateTagList();
    }

    function updateTagList() {
      if (!$scope.repositoryMap) {
        return;
      }
      let key = `${trigger.registry}/${trigger.repository}`;
      $scope.tags = $scope.repositoryMap[key] || [];
    }

    this.clearTag = function() {
      trigger.tag = null;
    };

    $scope.getTags = function(search) {
      var newOne = $scope.tags.slice();
      if (search && newOne.indexOf(search) === -1) {
        newOne.unshift(search);
      }
      return newOne;
    };

    function initializeImages() {
      loadImages().then(function (images) {
        $scope.images = images;
        $scope.registryMap = images.reduce((map, image) => {
          let key = image.registry;
          if (!key) {
            return map;
          }
          let all = map[key] || [];
          if (all.indexOf(image.repository) < 0) {
            map[key] = all.concat(image.repository);
          }
          return map;
        }, {});
        $scope.registries = Object.keys($scope.registryMap);
        updateRepositoryList();
        $scope.repositoryMap = images.reduce((map, image) => {
          let key = `${image.registry}/${image.repository}`;
          let all = map[key] || [];
          if (all.indexOf(image.tag) < 0) {
            map[key] = all.concat(image.tag);
          }
          return map;
        }, {});
        updateTagList();

        $scope.viewState.imagesLoaded = true;
        $scope.viewState.imagesRefreshing = false;
      });
    }

    this.refreshImages = function() {
      $scope.viewState.imagesRefreshing = true;
      initializeImages();
    };

    this.updateTrigger = function(image) {
      trigger.registry = image.registry;
      trigger.repository = image.repository;
      trigger.tag = image.tag;
    };

    initializeImages();

    $scope.$watch('trigger.registry', updateRepositoryList);
    $scope.$watch('trigger.repository', updateTagList);

  });
