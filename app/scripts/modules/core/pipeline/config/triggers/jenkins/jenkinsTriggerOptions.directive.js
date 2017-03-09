'use strict';

const angular = require('angular');
import {IGOR_SERVICE} from 'core/ci/igor.service';

module.exports = angular
  .module('spinnaker.core.pipeline.config.triggers.jenkins.options.directive', [
    IGOR_SERVICE
  ])
  .directive('jenkinsTriggerOptions', function () {
    return {
      restrict: 'E',
      templateUrl: require('./jenkinsTriggerOptions.directive.html'),
      bindToController: {
        command: '=',
      },
      controller: 'JenkinsTriggerOptionsCtrl',
      controllerAs: 'vm',
      scope: {}
    };
  })
  .controller('JenkinsTriggerOptionsCtrl', function ($scope, igorService) {
    // These fields will be added to the trigger when the form is submitted
    this.command.extraFields = {};

    this.viewState = {
      buildsLoading: true,
      loadError: false,
      selectedBuild: null,
    };

    let buildLoadSuccess = (builds) => {
      this.builds = builds.filter((build) => !build.building && build.result === 'SUCCESS')
        .sort((a, b) => b.number - a.number);
      if (this.builds.length) {
        let defaultSelection = this.builds[0];
        this.viewState.selectedBuild = defaultSelection;
        this.updateSelectedBuild(defaultSelection);
      }
      this.viewState.buildsLoading = false;
    };

    let buildLoadFailure = () => {
      this.viewState.buildsLoading = false;
      this.viewState.loadError = true;
    };

    let initialize = () => {
      let command = this.command;
      // do not re-initialize if the trigger has changed to some other type
      if (command.trigger.type !== 'jenkins') {
        return;
      }
      this.viewState.buildsLoading = true;
      igorService.listBuildsForJob(command.trigger.master, command.trigger.job)
        .then(buildLoadSuccess, buildLoadFailure);
    };

    this.updateSelectedBuild = (item) => {
      this.command.extraFields.buildNumber = item.number;
    };

    $scope.$watch(() => this.command.trigger, initialize);

  });
