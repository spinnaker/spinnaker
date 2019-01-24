'use strict';

const angular = require('angular');

import { AUTO_SCROLL_DIRECTIVE } from 'core/presentation/autoScroll/autoScroll.directive';
import { ANY_FIELD_FILTER } from './anyFieldFilter/anyField.filter';
import { PAGE_NAVIGATOR_COMPONENT } from './navigation/pageNavigator.component';
import { PAGE_SECTION_COMPONENT } from './navigation/pageSection.component';
import { REPLACE_FILTER } from './replace.filter';
import { ROBOT_TO_HUMAN_FILTER } from './robotToHumanFilter/robotToHuman.filter';

import './flex-layout.less';
import './details.less';
import './main.less';
import './navPopover.less';

module.exports = angular.module('spinnaker.core.presentation', [
  ANY_FIELD_FILTER,
  AUTO_SCROLL_DIRECTIVE,
  PAGE_NAVIGATOR_COMPONENT,
  PAGE_SECTION_COMPONENT,
  require('./collapsibleSection/collapsibleSection.directive').name,
  require('./isVisible/isVisible.directive').name,
  ROBOT_TO_HUMAN_FILTER,
  require('./sortToggle/sorttoggle.directive').name,
  require('./percent.filter').name,
  REPLACE_FILTER,
]);
