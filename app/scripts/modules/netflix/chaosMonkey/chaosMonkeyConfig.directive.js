'use strict';

const angular = require('angular');

require('./chaosMonkeyConfig.directive.less');

module.exports = angular
  .module('spinnaker.netflix.chaosMonkey.config.directive', [
    require('../../core/utils/lodash.js'),
    require('../../core/config/settings.js'),
    require('./chaosMonkeyExceptions.directive.js'),
    require('./chaosMonkeyConfigFooter.directive.js'),
  ])
  .directive('chaosMonkeyConfig', function () {
    return {
      restrict: 'E',
      templateUrl: require('./chaosMonkeyConfig.directive.html'),
      scope: {},
      bindToController: {
        application: '=',
      },
      controller: 'ChaosMonkeyConfigCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ChaosMonkeyConfigCtrl', function($scope, _, settings) {
    if (this.application.notFound) {
      return;
    }
    let defaults = {
        enabled: false,
        meanTimeBetweenKillsInWorkDays: 2,
        minTimeBetweenKillsInWorkDays: 1,
        grouping: 'cluster',
        regionsAreIndependent: true,
        exceptions: [],
      };

    let config = Object.assign({}, defaults, this.application.attributes.chaosMonkey || {});

    this.viewState = {
      originalConfig: _.cloneDeep(config),
      originalStringVal: JSON.stringify(config),
      saving: false,
      saveError: false,
      isDirty: false,
    };

    this.config = _.cloneDeep(config);

    this.chaosEnabled = settings.feature && settings.feature.chaosMonkey;

    this.groupingOptions = [
      { key: 'app', label: 'App' },
      { key: 'stack', label: 'Stack' },
      { key: 'cluster', label: 'Cluster' },
    ];

    this.configChanged = () => {
      this.viewState.isDirty = this.viewState.originalStringVal !== JSON.stringify(this.config);
    };

    $scope.$watch(() => this.config, this.configChanged, true);

  });
