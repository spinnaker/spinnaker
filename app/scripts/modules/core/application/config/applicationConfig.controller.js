'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.controller', [
    require('angular-ui-router'),
    require('./applicationAttributes.directive.js'),
    require('./applicationNotifications.directive.js'),
    require('./applicationCacheManagement.directive.js'),
    require('./deleteApplicationSection.directive.js'),
    require('./serializeApplicationSection.component.js'),
    require('./links/applicationLinks.component.js'),
    require('../../config/settings.js')
  ])
  .controller('ApplicationConfigController', function ($state, app, settings) {
    this.application = app;
    this.serialization = settings.feature.serialization;
    if (app.notFound) {
      $state.go('home.infrastructure', null, {location: 'replace'});
    }
  });
