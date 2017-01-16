'use strict';

import {Observable, Subject} from 'rxjs';
import _ from 'lodash';

import {DOCKER_IMAGE_READER} from 'docker/image/docker.image.reader.service';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.triggers.docker.options.directive', [
    require('core/config/settings.js'),
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
      // cancel search stream if trigger has changed to some other type
      if (this.command.trigger.type !== 'docker') {
        subscription.unsubscribe();
        return;
      }
      this.searchTags();
    };

    let handleQuery = (q) => {
      return Observable.fromPromise(
          dockerImageReader.findImages({
            provider: 'dockerRegistry',
            account: this.command.trigger.account,
            q: ( this.command.trigger.organization ? this.command.trigger.organization + '/' : '' ) + q,
            count: 50 }));
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
      queryStream.next(formatQuery(query));
    };

    let formatQuery = (query) => {
      let repository = _.get(this, 'command.trigger.repository');
      if (repository) {
        return `${this.command.trigger.repository.split('/').pop()}:${query}`;
      } else {
        return query;
      }
    };

    $scope.$watch(() => this.command.trigger, initialize);
  });
