'use strict';

let angular = require('angular');

require('./../tableau.less');

module.exports = angular
  .module('spinnaker.netflix.tableau.summary.controller', [
    require('../../../core/config/settings'),
  ])
  .controller('SummaryTableauCtrl', function ($http, $sce, settings) {
    this.tokenRetrieved = (token) => {
      this.token = token;
      let url = settings.tableau.summarySourceUrl.replace('${token}', token);
      this.srcUrl = $sce.trustAsResourceUrl(url);
    };

    this.handleError = (error) => {
      this.error = error;
    };

    $http.get('http://deaproxy.dyntest.netflix.net:7101/tableau/trusted_token')
      .then(resp => resp.data, this.handleError)
      .then(this.tokenRetrieved, this.handleError);

  });
