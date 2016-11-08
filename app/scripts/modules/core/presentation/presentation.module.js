'use strict';

import {ANY_FIELD_FILTER} from './anyFieldFilter/anyField.filter';
import {AUTO_SCROLL_DIRECTIVE} from 'core/presentation/autoScroll/autoScroll.directive';
import {PAGE_NAVIGATOR_COMPONENT} from './navigation/pageNavigator.component';
import {PAGE_SECTION_COMPONENT} from './navigation/pageSection.component';

let angular = require('angular');

require('./details.less');
require('./main.less');
require('./navPopover.less');

module.exports = angular.module('spinnaker.core.presentation', [
  ANY_FIELD_FILTER,
  AUTO_SCROLL_DIRECTIVE,
  PAGE_NAVIGATOR_COMPONENT,
  PAGE_SECTION_COMPONENT,
  require('./collapsibleSection/collapsibleSection.directive.js'),
  require('./gist/gist.directive.js'),
  require('./isVisible/isVisible.directive.js'),
  require('./robotToHumanFilter/robotToHuman.filter.js'),
  require('./sortToggle/sorttoggle.directive.js'),
]);
