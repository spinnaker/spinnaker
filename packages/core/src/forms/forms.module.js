'use strict';

import { module } from 'angular';

import { CORE_FORMS_AUTOFOCUS_AUTOFOCUS_DIRECTIVE } from './autofocus/autofocus.directive';
import { CORE_FORMS_CHECKLIST_CHECKLIST_DIRECTIVE } from './checklist/checklist.directive';
import { CORE_FORMS_CHECKMAP_CHECKMAP_DIRECTIVE } from './checkmap/checkmap.directive';
import { CORE_FORMS_IGNOREEMPTYDELETE_DIRECTIVE } from './ignoreEmptyDelete.directive';
import { CORE_FORMS_MAPEDITOR_MAPEDITOR_COMPONENT } from './mapEditor/mapEditor.component';
import { NUMBER_LIST_COMPONENT } from './numberList/numberList.component';
import { CORE_FORMS_VALIDATEONSUBMIT_VALIDATEONSUBMIT_DIRECTIVE } from './validateOnSubmit/validateOnSubmit.directive';

export const CORE_FORMS_FORMS_MODULE = 'spinnaker.core.forms';
export const name = CORE_FORMS_FORMS_MODULE; // for backwards compatibility
module(CORE_FORMS_FORMS_MODULE, [
  CORE_FORMS_AUTOFOCUS_AUTOFOCUS_DIRECTIVE,
  CORE_FORMS_CHECKLIST_CHECKLIST_DIRECTIVE,
  CORE_FORMS_CHECKMAP_CHECKMAP_DIRECTIVE,
  CORE_FORMS_IGNOREEMPTYDELETE_DIRECTIVE,
  CORE_FORMS_MAPEDITOR_MAPEDITOR_COMPONENT,
  CORE_FORMS_VALIDATEONSUBMIT_VALIDATEONSUBMIT_DIRECTIVE,
  NUMBER_LIST_COMPONENT,
]);
