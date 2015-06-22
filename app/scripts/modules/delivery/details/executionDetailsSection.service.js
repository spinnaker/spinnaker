'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.executionDetails.section.service', [
  require('angular-ui-router'),
])
  .factory('executionDetailsSectionService', function($stateParams, $state) {

    function sectionIsValid(availableSections) {
      return availableSections.indexOf($stateParams.details) !== -1;
    }

    function synchronizeSection(availableSections) {
      if (!$state.includes('**.execution')) {
        return false;
      }
      var details = $stateParams.details || availableSections[0];
      if (availableSections.indexOf(details) === -1) {
        details = availableSections[0];
      }
      if (!sectionIsValid(availableSections)) {
        // use { location: 'replace' } to overwrite the invalid browser history state
        $state.go('.', { details: details}, { location: 'replace' });
      }
      return true;
    }

    return {
      synchronizeSection: synchronizeSection
    };

  });
