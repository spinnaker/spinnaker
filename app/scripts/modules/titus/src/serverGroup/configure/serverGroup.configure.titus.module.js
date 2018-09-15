'use strict';

const angular = require('angular');

import { TITUS_SERVER_GROUP_CONFIGURATION_SERVICE } from './serverGroupConfiguration.service';

module.exports = angular.module('spinnaker.serverGroup.configure.titus', [TITUS_SERVER_GROUP_CONFIGURATION_SERVICE]);
