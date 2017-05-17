'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.application.inferredApplicationWarning.service', [
    require('../widgets/notifier/notifier.service.js'),
  ])
  .factory('inferredApplicationWarning', function (notifierService) {

    let viewedApplications = {};

    function checkIfInferredAndWarn (app) {
      if (check(app)) {
        warn(app.name);
      }
    }

    function check (app) {
      let name = app.name,
        hasViewedApplication = viewedApplications[name];

      markAsViewed(name);

      return !hasViewedApplication && isInferredApplication(app);
    }

    function warn (appName) {
      notifierService.publish({
        key: 'inferredApplicationWarning',
        position: 'bottom',
        body: `
          The application <b>${appName}</b>
          has not been <a role="button" href="#/applications/${appName}/config">configured.</a>`
      });
    }

    function isInferredApplication (app) {
      return _.find(['accounts', 'email'], (key) => !app.attributes[key]);
    }

    function markAsViewed (appName) {
      viewedApplications[appName] = true;
    }

    function resetViewedApplications () {
      viewedApplications = {};
    }

    return { checkIfInferredAndWarn, resetViewedApplications };
  });
