'use strict';

const angular = require('angular');

require('./reservationReport.directive.less');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.report.reservationReport.directive', [
    require('./reservationReport.read.service.js'),
    require('core/account/accountTag.directive')
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
        isVpc: '=',
      },
      controller: 'ReservationReportCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ReservationReportCtrl', function ($scope, reservationReportReader) {
    this.viewState = {
      loading: true,
      error: false,
    };

    let setVpc = () => {
      this.reportData.forEach((row) => {
        row.display = {
          reserved: this.isVpc ? row.reservations.reservedVpc : row.reservations.reserved,
          used: this.isVpc ? row.reservations.usedVpc : row.reservations.used,
          surplus: this.isVpc ? row.reservations.surplusVpc : row.reservations.surplus,
        };
      });
    };

    let setReportData = () => {
      if (this.viewState.loading || !this.account || !this.region || !this.instanceType || !this.zones) {
        return;
      }
      let reportData = reservationReportReader.extractReservations(this.report.reservations, this.account, this.region, this.instanceType);
      this.reportData = reportData.filter((row) => this.zones.includes(row.availabilityZone));
      setVpc();
    };

    let initialize = () => {
      reservationReportReader.getReservations()
        .then(
        (report) => {
          this.report = report;
          this.viewState.loading = false;
          setReportData();
        },
        () => {
          this.viewState.loading = false;
          this.viewState.error = true;
        }
      );
    };

    initialize();
    $scope.$watchCollection(() => [this.instanceType, this.account, this.region, this.isVpc, this.zones], setReportData);
  });
