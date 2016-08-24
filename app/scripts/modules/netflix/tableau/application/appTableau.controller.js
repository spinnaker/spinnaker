'use strict';

let angular = require('angular');

require('./../tableau.less');

module.exports = angular
  .module('spinnaker.netflix.tableau.application.controller', [
    require('../../../core/config/settings'),
  ])
  .controller('AppTableauCtrl', function ($http, $sce, app, settings) {
    this.tokenRetrieved = (token) => {
      this.token = token;
      let url = settings.tableau.appSourceUrl.replace('${token}', token).replace('${app}', app.name);
      this.srcUrl = $sce.trustAsResourceUrl(url);
    };

    this.handleError = (error) => {
      this.error = error;
    };

    $http.get(settings.tableau.tokenUrl)
      .then(resp => resp.data, this.handleError)
      .then(this.tokenRetrieved, this.handleError);

  });
