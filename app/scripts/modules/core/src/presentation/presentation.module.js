'use strict';

const angular = require('angular');

import { AUTO_SCROLL_DIRECTIVE } from 'core/presentation/autoScroll/autoScroll.directive';
import { ANY_FIELD_FILTER } from './anyFieldFilter/anyField.filter';
import { PAGE_NAVIGATOR_COMPONENT } from './navigation/pageNavigator.component';
import { PAGE_SECTION_COMPONENT } from './navigation/pageSection.component';
import { REPLACE_FILTER } from './replace.filter';
import { ROBOT_TO_HUMAN_FILTER } from './robotToHumanFilter/robotToHuman.filter';
import { domPurifyOpenLinksInNewWindow } from './domPurifyOpenLinksInNewWindow';

import './flex-layout.less';
import './details.less';
import './main.less';
import './navPopover.less';

export const CORE_PRESENTATION_PRESENTATION_MODULE = 'spinnaker.core.presentation';
export const name = CORE_PRESENTATION_PRESENTATION_MODULE; // for backwards compatibility
angular
  .module(CORE_PRESENTATION_PRESENTATION_MODULE, [
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
  ])
  .run(domPurifyOpenLinksInNewWindow);
