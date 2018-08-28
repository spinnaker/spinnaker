'use strict';

import { SubnetTag } from './SubnetTag';
import { react2angular } from 'react2angular';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.subnet.tag.component', [])
  .component('subnetTag', react2angular(SubnetTag, ['subnetId']));
