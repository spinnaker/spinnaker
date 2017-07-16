'use strict';

const angular = require('angular');

import _ from 'lodash';

import { NOTIFIER_SERVICE } from 'core/widgets/notifier/notifier.service';

module.exports = angular.module('spinnaker.deck.core.application.inferredApplicationWarning.service', [
    NOTIFIER_SERVICE,
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
