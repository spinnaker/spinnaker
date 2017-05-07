import { module } from 'angular';
import { react2angular } from 'react2angular';

import { GlobalPropertiesList } from './GlobalPropertiesList';

export const GLOBAL_PROPERTIES_COMPONENT = 'spinnaker.netflix.fastProperties.globalProperties.component';
module(GLOBAL_PROPERTIES_COMPONENT, [
])
  .component('globalPropertiesList', react2angular(GlobalPropertiesList, ['app']));
