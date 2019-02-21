'use strict';

const angular = require('angular');

import { JsonUtils } from 'core/utils';
import { DIFF_VIEW_COMPONENT } from './diffView.component';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

import './showHistory.less';

module.exports = angular
  .module('spinnaker.core.pipeline.config.actions.history.controller', [
    require('./diffSummary.component').name,
    DIFF_VIEW_COMPONENT,
  ])
  .controller('ShowHistoryCtrl', ['$window', 'pipelineConfigId', 'currentConfig', 'isStrategy', '$uibModalInstance', '$filter', function(
    $window,
    pipelineConfigId,
    currentConfig,
    isStrategy,
    $uibModalInstance,
    $filter,
  ) {
    this.state = {
      loading: true,
      error: false,
    };

    this.version = 0;
    this.compareOptions = ['previous version', 'current'];
    this.compareTo = this.compareOptions[0];

    let historyLoaded = history => {
      this.state.loading = false;
      if (currentConfig) {
        history = [currentConfig].concat(history);
      }
      this.history = history.map((h, index) => {
        let ts = h.updateTs;
        delete h.updateTs;
        return {
          timestamp: $filter('timestamp')(ts),
          contents: JSON.stringify(h, null, 2),
          json: h,
          index: index,
        };
      });
      this.history[0].timestamp += ' (current)';
      if (currentConfig) {
        this.history[0].timestamp = 'Current config (not saved)';
        this.history[1].timestamp += ' (last saved)';
      }
      this.updateDiff();
    };

    let loadError = () => {
      this.state.loading = false;
      this.state.error = true;
    };

    this.updateDiff = () => {
      this.right = this.history[this.version].contents;
      if (this.compareTo === this.compareOptions[1]) {
        this.left = this.history[0].contents;
      } else {
        this.left = this.right;
        if (this.version < this.history.length - 1) {
          this.left = this.history[this.version + 1].contents;
        }
      }
      this.diff = JsonUtils.diff(this.left, this.right, true);
    };

    this.close = $uibModalInstance.dismiss;

    this.restoreVersion = () => {
      $uibModalInstance.close(this.history[this.version].json);
    };

    PipelineConfigService.getHistory(pipelineConfigId, isStrategy, 100).then(historyLoaded, loadError);
  }]);
