'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.controller', [
    require('angular-ui-router'),
    require('./applicationAttributes.directive.js'),
    require('./applicationNotifications.directive.js'),
    require('./applicationCacheManagement.directive.js'),
    require('./deleteApplicationSection.directive.js'),
  ])
  .controller('ApplicationConfigController', function ($state, app) {
    this.application = app;
    if (app.notFound) {
      $state.go('home.infrastructure', null, {location: 'replace'});
    }
  }).name;
