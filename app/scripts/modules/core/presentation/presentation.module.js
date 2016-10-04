'use strict';
import anyFieldFilter from './anyFieldFilter/anyField.filter';

let angular = require('angular');

require('./details.less');
require('./main.less');
require('./navPopover.less');

module.exports = angular.module('spinnaker.core.presentation', [
  anyFieldFilter,
  require('./autoScroll/autoScroll.directive.js'),
  require('./collapsibleSection/collapsibleSection.directive.js'),
  require('./gist/gist.directive.js'),
  require('./isVisible/isVisible.directive.js'),
  require('./robotToHumanFilter/robotToHuman.filter.js'),
  require('./sortToggle/sorttoggle.directive.js'),
]);
