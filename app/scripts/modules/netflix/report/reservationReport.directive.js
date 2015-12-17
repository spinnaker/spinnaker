'use strict';

const angular = require('angular');

require('./reservationReport.directive.less');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.report.reservationReport.directive', [
    require('./reservationReport.read.service.js'),
  ])
  .directive('reservationReport', function() {
    return {
      restrict: 'E',
      templateUrl: require('./reservationReport.directive.html'),
      scope: {},
      bindToController: {
        account: '=',
        region: '=',
        instanceType: '=',
        zones: '=',
      },
      controller: 'ReservationReportCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ReservationReportCtrl', function (reservationReportReader) {
    this.viewState = {
      loading: true,
      error: false,
    };

    reservationReportReader.getReservationsFor(this.account, this.region, this.instanceType)
      .then(
      (report) => {
        this.report = report.filter((row) => this.zones.indexOf(row.availabilityZone) > -1);
        this.viewState.loading = false;
      },
      () => {
        this.viewState.loading = false;
        this.viewState.error = true;
      }
    );
  });
