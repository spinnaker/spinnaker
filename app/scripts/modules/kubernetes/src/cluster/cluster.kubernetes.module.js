'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.cluster.kubernetes', [require('./configure/CommandBuilder').name]);
