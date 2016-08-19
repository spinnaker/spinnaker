'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.triggers.docker.options.directive', [
    require('../../../../../core/config/settings.js'),
    require('../../../../../docker/image/image.reader.js'),
    require('../../../../../core/utils/rx.js'),
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
  .controller('dockerTriggerOptionsCtrl', function ($scope, dockerImageReader, rx) {
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
        subscription.dispose();
        return;
      }
      this.searchTags();
    };

    let handleQuery = (q) => {
      return rx.Observable.fromPromise(
          dockerImageReader.findImages({
            provider: 'dockerRegistry',
            account: this.command.trigger.account,
            q: q,
            count: 50 }));
    };

    this.updateSelectedTag = (item) => {
      this.command.extraFields.tag = item;
    };

    let queryStream = new rx.Subject();

    let subscription = queryStream
      .debounce(250)
      .flatMapLatest(handleQuery)
      .subscribe((results) => $scope.$apply(() => tagLoadSuccess(results)), () => $scope.$apply(tagLoadFailure));

    this.searchTags = (query = '') => {
      this.tags = [`<span>Finding tags${query && ` matching ${query}`}...</span>`];
      queryStream.onNext(formatQuery(query));
    };

    let formatQuery = (query) => `*${this.command.trigger.repository.split('/').pop()}:${query}*`;

    $scope.$watch(() => this.command.trigger, initialize);
  });
