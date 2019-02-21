'use strict';

const angular = require('angular');

import { AuthenticationService, Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.titus.pipeline.stage.titusBakeStage', [require('./bakeExecutionDetails.controller').name])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'bake',
      useBaseProvider: true,
      cloudProvider: 'titus',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      validators: [],
    });
  })
  .controller('titusBakeCtrl', ['$scope', function($scope) {
    let stage = $scope.stage;

    if (!stage.user) {
      stage.user = AuthenticationService.getAuthenticatedUser().name;
    }

    if (!stage.regions) {
      stage.regions = ['us-west-1'];
    }

    if (!stage.storeType) {
      stage.storeType = 'docker';
    }

    if (!stage.baseOs) {
      stage.baseOs = 'trusty';
    }

    if (!stage.repository) {
      stage.repository = {};
    }

    if (!stage.repository.fromGitTrigger && stage.repository.fromGitTrigger !== false) {
      stage.repository.fromGitTrigger = true;
    }

    if (!stage.repository.buildParameters) {
      stage.repository.buildParameters = {};
    }

    if (!stage.image) {
      stage.image = {};
    }

    this.updateRepository = () => {
      let url = '';
      if (stage.repository.fromGitTrigger) {
        url = 'ssh://git@stash.corp.netflix.com:7999/${trigger.project}/${trigger.slug}.git';
        if (stage.repository.directory) {
          url += '/' + stage.repository.directory;
        }
        url += '?${trigger.hash}@${trigger.branch}';
      } else {
        url = stage.repository.url;
        if (stage.repository.directory) {
          url += '/' + stage.repository.directory;
        }
        if (stage.repository.hash) {
          url += stage.repository.hash;
        }
        if (stage.repository.branch) {
          url += '@' + stage.repository.branch;
        }
      }

      let buildParams = $.param(stage.repository.buildParameters);
      if (buildParams.length > 0) {
        url += '&' + decodeURIComponent(buildParams);
      }
      stage.package = url;
    };

    this.updateImage = () => {
      let image = '';
      if (stage.image.organization) {
        image += stage.image.organization + '/';
      }
      if (stage.image.name) {
        image += stage.image.name;
      }
      if (stage.image.tags) {
        image += ':' + stage.image.tags;
      }
      stage.amiName = image;
    };

    $scope.$watch('stage.repository', this.updateRepository, true, true);
    $scope.$watch('stage.image', this.updateImage, true, true);
  }]);
