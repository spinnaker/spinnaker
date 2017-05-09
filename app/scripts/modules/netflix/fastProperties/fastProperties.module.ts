import { module } from 'angular';

import { FAST_PROPERTY_SEARCH_COMPONENT } from './view/filter/fastPropertyFilterSearch.component';
import { FAST_PROPERTY_DETAILS_CONTROLLER } from './view/details/fastPropertyDetails.controller';
import { CREATE_FAST_PROPERTY_WIZARD_CONTROLLER } from './wizard/createFastPropertyWizard.controller';
import { FAST_PROPERTIES_HELP } from './fastProperties.help';
import { FAST_PROPERTY_STATES} from './fastProperties.states';

import './fastProperties.less';
import './global/fastPropertyFilterSearch.less';
import 'netflix/canary/canary.less';

export const FAST_PROPERTIES_MODULE = 'spinnaker.netflix.fastProperties';
module(FAST_PROPERTIES_MODULE, [
    FAST_PROPERTY_STATES,
    CREATE_FAST_PROPERTY_WIZARD_CONTROLLER,
    FAST_PROPERTY_DETAILS_CONTROLLER,
    FAST_PROPERTIES_HELP,
    require('./fastProperty.dataSource'),
    FAST_PROPERTY_SEARCH_COMPONENT,
  ]);
