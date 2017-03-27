'use strict';

let angular = require('angular');

import {SETTINGS} from 'core/config/settings';

module.exports = angular
  .module('spinnaker.amazon.instance.report.reservation.read.service', [])
  .factory('reservationReportReader', function ($http) {

    function getReservations() {
      return $http.get([SETTINGS.gateUrl, 'reports', 'reservation', 'v2'].join('/'))
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
