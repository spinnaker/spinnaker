'use strict';

import {ANY_FIELD_FILTER} from './anyFieldFilter/anyField.filter';
import {AUTO_SCROLL_DIRECTIVE} from 'core/presentation/autoScroll/autoScroll.directive';

let angular = require('angular');

require('./details.less');
require('./main.less');
require('./navPopover.less');

module.exports = angular.module('spinnaker.core.presentation', [
  ANY_FIELD_FILTER,
  AUTO_SCROLL_DIRECTIVE,
  require('./collapsibleSection/collapsibleSection.directive.js'),
  require('./gist/gist.directive.js'),
  require('./isVisible/isVisible.directive.js'),
  require('./robotToHumanFilter/robotToHuman.filter.js'),
  require('./sortToggle/sorttoggle.directive.js'),
]);
