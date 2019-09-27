'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.details.kubernetes', [require('./details.controller').name]);
