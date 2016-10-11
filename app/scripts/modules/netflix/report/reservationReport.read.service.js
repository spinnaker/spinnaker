'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.instance.report.reservation.read.service', [
    require('core/config/settings.js'),
  ])
  .factory('reservationReportReader', function ($http, settings) {

    function getReservations() {
      return $http.get([settings.gateUrl, 'reports', 'reservation', 'v2'].join('/'))
          .then((response) => response.data);
    }

    function extractReservations(reservations, account, region, instanceType) {
      return reservations
        .filter((reservation) => reservation.region === region &&
          reservation.instanceType === instanceType &&
          reservation.accounts[account])
        .map((reservation) => {
          return {
            availabilityZone: reservation.availabilityZone,
            os: reservation.os,
            reservations: reservation.accounts[account],
          };
        });
    }

    return {
      getReservations: getReservations,
      extractReservations: extractReservations,
    };
  });
