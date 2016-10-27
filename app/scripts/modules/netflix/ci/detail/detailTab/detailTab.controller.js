'use strict';

import {Subject} from 'rxjs';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.ci.detail.detailTab.controller', [
    require('angular-ui-router'),
    require('../../build.read.service.js'),
    require('core/scheduler/scheduler.factory'),
  ])
  .controller('CiDetailTabCtrl', function ($scope, $state, $stateParams, buildService, schedulerFactory, app) {
    const dataSource = app.getDataSource('ci');

    this.viewState = {
      loading: true,
      autoScrollEnabled: false,
      stickyHeaderEnabled: false,
      isRunning: false,
      tab: $stateParams.tab,
      showLoadMore: false,
      loadingMore: false,
    };

    this.scrollToTopSubject = new Subject();

    this.scrollToTop = () => this.scrollToTopSubject.next(true);

    this.content = [];

    $scope.$on('sticky-header-enabled', (event, styles) => {
      $scope.$apply(() => {
        this.viewState.stickyHeaderEnabled = true;
        this.viewState.headerStyles = styles;
      });
    });

    $scope.$on('sticky-header-disabled', () => {
      $scope.$apply(() => {
        this.viewState.stickyHeaderEnabled = false;
      });
    });

    this.toggleAutoScroll = () => {
      this.viewState.autoScrollEnabled = !this.viewState.autoScrollEnabled;
    };

    this.handleScroll = ($event) => {
      let newY = $event.target.scrollTop;
      if (this.previousY) {
        if (newY < this.previousY) {
          this.viewState.autoScrollEnabled = false;
        }
      }
      this.previousY = newY;
    };

    this.getOutput = () => {
      this.viewState.loadingMore = true;
      this.viewState.showLoadMore = false;
      return buildService.getBuildOutput($stateParams.buildId, this.content.length).then((response) => {
        if (response.data.length) {
          this.content.push(...response.data);
          this.viewState.loading = false;
        }
        this.viewState.loadingMore = false;
        if (!this.viewState.isRunning && response.data.length && this.content.length % (buildService.MAX_LINES + 1) === 0) {
          this.viewState.showLoadMore = true;
        } else {
          this.viewState.showLoadMore = false;
        }
      });
    };

    let setRunningFlag = () => {
      let build = dataSource.data.find(b => b.id === $stateParams.buildId);
      if (build) {
        this.viewState.isRunning = build.isRunning;
      } else {
        this.viewState.isRunning = false;
      }
    };

    dataSource.onRefresh($scope, setRunningFlag);

    if ($stateParams.tab === 'config') {
      buildService.getBuildConfig($stateParams.buildId).then((response) => {
        this.viewState.loading = false;
        this.content = response.data;
      });
    }

    if ($stateParams.tab === 'output') {
      dataSource.ready().then(this.getOutput);
      let activeRefresher = schedulerFactory.createScheduler(1000);
      activeRefresher.subscribe(() => {
        if (this.viewState.isRunning) {
          this.getOutput();
        }
      });
      $scope.$on('$destroy', () => activeRefresher.unsubscribe());
    }
  });
