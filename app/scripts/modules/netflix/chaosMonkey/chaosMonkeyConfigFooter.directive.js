'use strict';

const angular = require('angular');

require('./chaosMonkeyConfigFooter.directive.less');

module.exports = angular
  .module('spinnaker.netflix.chaosMonkey.config.footer.directive', [
    require('../../core/application/service/applications.write.service.js'),
  ])
  .directive('chaosMonkeyConfigFooter', function () {
    return {
      restrict: 'E',
      templateUrl: require('./chaosMonkeyConfigFooter.directive.html'),
      scope: {},
      bindToController: {
        application: '=',
        config: '=',
        viewState: '=',
      },
      controller: 'ChaosMonkeyConfigFooterCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ChaosMonkeyConfigFooterCtrl', function($scope, applicationWriter) {

    this.revert = () => {
      angular.copy(this.viewState.originalConfig, this.config);
    };

    this.save = () => {
      this.viewState.saving = true;
      this.viewState.saveError = false;
      applicationWriter.updateApplication({
        name: this.application.name,
        accounts: this.application.attributes.accounts,
        chaosMonkey: this.config
      })
        .then(() => {
          this.viewState.originalConfig = this.config;
          this.viewState.originalStringVal = JSON.stringify(this.config);
          this.viewState.isDirty = false;
          this.viewState.saving = false;
        }, () => {
          this.viewState.saving = false;
          this.viewState.saveError = true;
        });
    };

  });
