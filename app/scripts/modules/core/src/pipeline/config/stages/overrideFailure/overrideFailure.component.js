'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.overrideFailure', []).component('overrideFailure', {
  bindings: {
    stage: '=',
  },
  templateUrl: require('./overrideFailure.component.html'),
  controller: [
    '$scope',
    function($scope) {
      this.viewState = {};

      this.failureOptionChanged = () => {
        if (this.viewState.failureOption === 'fail') {
          this.stage.failPipeline = true;
          this.stage.continuePipeline = false;
          this.stage.completeOtherBranchesThenFail = false;
        } else if (this.viewState.failureOption === 'stop') {
          this.stage.failPipeline = false;
          this.stage.continuePipeline = false;
          this.stage.completeOtherBranchesThenFail = false;
        } else if (this.viewState.failureOption === 'ignore') {
          this.stage.failPipeline = false;
          this.stage.continuePipeline = true;
          this.stage.completeOtherBranchesThenFail = false;
        } else if (this.viewState.failureOption === 'faileventual') {
          this.stage.failPipeline = false;
          this.stage.continuePipeline = false;
          this.stage.completeOtherBranchesThenFail = true;
        }
      };

      this.initializeFailureOption = () => {
        var initValue = 'fail';
        if (this.stage.completeOtherBranchesThenFail === true) {
          initValue = 'faileventual';
        } else if (this.stage.failPipeline === true && this.stage.continuePipeline === false) {
          initValue = 'fail';
        } else if (this.stage.failPipeline === false && this.stage.continuePipeline === false) {
          initValue = 'stop';
        } else if (this.stage.failPipeline === false && this.stage.continuePipeline === true) {
          initValue = 'ignore';
        }
        this.viewState.failureOption = initValue;
      };

      $scope.$watch(() => this.stage, this.initializeFailureOption);
    },
  ],
});
