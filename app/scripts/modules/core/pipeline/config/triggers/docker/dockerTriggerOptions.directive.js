'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.triggers.docker.options.directive', [
    require('../../../../../core/config/settings.js'),
    require('../../../../../docker/image/image.reader.js'),
  ])
  .directive('dockerTriggerOptions', function () {
    return {
      restrict: 'E',
      templateUrl: require('./dockerTriggerOptions.directive.html'),
      bindToController: {
        command: '=',
      },
      controller: 'dockerTriggerOptionsCtrl',
      controllerAs: 'vm',
      scope: {}
    };
  })
  .controller('dockerTriggerOptionsCtrl', function ($scope, dockerImageReader) {
    // These fields will be added to the trigger when the form is submitted
    this.command.extraFields = {};

    this.viewState = {
      tagsLoading: false,
      loadError: false,
      selectedTag: null,
    };

    let tagLoadSuccess = (tags) => {
      this.tags = tags.map((val) => val.tag ).sort();
      if (this.tags.length) {
        let defaultSelection = this.tags[0];
        this.viewState.selectedTag = defaultSelection;
        this.updateSelectedTag(defaultSelection);
      }
      this.viewState.tagsLoading = false;
    };

    let tagLoadFailure = () => {
      this.viewState.tagsLoading = false;
      this.viewState.loadError = true;
    };

    let initialize = () => {
      let command = this.command;
      // do not re-initialize if the trigger has changed to some other type
      if (command.trigger.type !== 'docker') {
        return;
      }
      this.viewState.tagsLoading = true;

      dockerImageReader.findImages({ provider: 'dockerRegistry', account: command.trigger.account, q: command.trigger.repository }).then(tagLoadSuccess, tagLoadFailure);
    };

    this.updateSelectedTag = (item) => {
      this.command.extraFields.tag = item;
    };

    $scope.$watch(() => this.command.trigger, initialize);

  });
