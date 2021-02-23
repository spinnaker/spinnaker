'use strict';

import { module } from 'angular';

import { ANY_FIELD_FILTER } from './anyFieldFilter/anyField.filter';
import { AUTO_SCROLL_DIRECTIVE } from './autoScroll/autoScroll.directive';
import { CORE_PRESENTATION_COLLAPSIBLESECTION_COLLAPSIBLESECTION_DIRECTIVE } from './collapsibleSection/collapsibleSection.directive';
import { domPurifyOpenLinksInNewWindow } from './domPurifyOpenLinksInNewWindow';
import { CORE_PRESENTATION_ISVISIBLE_ISVISIBLE_DIRECTIVE } from './isVisible/isVisible.directive';
import { LINK_WITH_CLIPBOARD } from './linkWithClipboard.component';
import { CORE_PRESENTATION_MARKDOWN } from './markdown.component';
import { PAGE_NAVIGATOR_COMPONENT } from './navigation/pageNavigator.component';
import { PAGE_SECTION_COMPONENT } from './navigation/pageSection.component';
import { CORE_PRESENTATION_PERCENT_FILTER } from './percent.filter';
import { REPLACE_FILTER } from './replace.filter';
import { ROBOT_TO_HUMAN_FILTER } from './robotToHumanFilter/robotToHuman.filter';
import { CORE_PRESENTATION_SORTTOGGLE_SORTTOGGLE_DIRECTIVE } from './sortToggle/sorttoggle.directive';

import './details.less';
import './flex-layout.less';
import './main.less';
import './navPopover.less';

export const CORE_PRESENTATION_PRESENTATION_MODULE = 'spinnaker.core.presentation';
export const name = CORE_PRESENTATION_PRESENTATION_MODULE; // for backwards compatibility
module(CORE_PRESENTATION_PRESENTATION_MODULE, [
  ANY_FIELD_FILTER,
  AUTO_SCROLL_DIRECTIVE,
  LINK_WITH_CLIPBOARD,
  PAGE_NAVIGATOR_COMPONENT,
  PAGE_SECTION_COMPONENT,
  CORE_PRESENTATION_COLLAPSIBLESECTION_COLLAPSIBLESECTION_DIRECTIVE,
  CORE_PRESENTATION_ISVISIBLE_ISVISIBLE_DIRECTIVE,
  CORE_PRESENTATION_MARKDOWN,
  ROBOT_TO_HUMAN_FILTER,
  CORE_PRESENTATION_SORTTOGGLE_SORTTOGGLE_DIRECTIVE,
  CORE_PRESENTATION_PERCENT_FILTER,
  REPLACE_FILTER,
]).run(domPurifyOpenLinksInNewWindow);
