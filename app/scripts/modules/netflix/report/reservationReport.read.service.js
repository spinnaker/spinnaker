'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.instance.report.reservation.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../core/utils/lodash.js'),
  ])
  .factory('reservationReportReader', function (Restangular) {
    function getReservationsFor(account, region, instanceType) {
      return Restangular.one('reports', 'reservation')
        .get()
        .then((result) => {
          return extractReservations(result.reservations, account, region, instanceType);
        });
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
      getReservationsFor: getReservationsFor
    };
  });
