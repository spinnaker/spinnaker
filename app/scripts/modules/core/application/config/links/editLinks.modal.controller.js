'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.links.editJson', [])
  .controller('EditLinksModalCtrl', function(sections, $uibModalInstance) {

    this.cancel = $uibModalInstance.dismiss;

    this.initialize = () => {
      this.command = angular.toJson(sections, 2);
    };

    this.update = () => {
      try {
        $uibModalInstance.close(JSON.parse(this.command));
      } catch (e) {
        this.invalid = true;
        this.errorMessage = e.message;
      }
    };

    this.initialize();

  });

