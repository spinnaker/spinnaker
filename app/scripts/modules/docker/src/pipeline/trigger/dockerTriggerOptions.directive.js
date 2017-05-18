'use strict';

const angular = require('angular');
import { Observable, Subject } from 'rxjs';

import { DOCKER_IMAGE_READER } from '@spinnaker/docker';

module.exports = angular
  .module('spinnaker.docker.pipeline.config.triggers.options.directive', [
    DOCKER_IMAGE_READER,
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
      tagsLoading: true,
      loadError: false,
      selectedTag: null,
    };

    let tagLoadSuccess = (tags) => {
      this.tags = tags;
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
      // cancel search stream if trigger has changed to some other type
      if (this.command.trigger.type !== 'docker') {
        subscription.unsubscribe();
        return;
      }
      this.searchTags();
    };

    let handleQuery = () => {
      return Observable.fromPromise(
        dockerImageReader.findTags({
          provider: 'dockerRegistry',
          account: this.command.trigger.account,
          repository: this.command.trigger.repository,
        }));
    };

    this.updateSelectedTag = (item) => {
      this.command.extraFields.tag = item;
    };

    let queryStream = new Subject();

    let subscription = queryStream
      .debounceTime(250)
      .switchMap(handleQuery)
      .subscribe(tagLoadSuccess, tagLoadFailure);

    this.searchTags = (query = '') => {
      this.tags = [`<span>Finding tags${query && ` matching ${query}`}...</span>`];
      queryStream.next();
    };

    $scope.$watch(() => this.command.trigger, initialize);
  });
