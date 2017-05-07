import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ApplicationProperties } from './ApplicationProperties';

export const APPLICATION_PROPERTIES_COMPONENT = 'spinnaker.netflix.fastProperties.applicationProperties.component';
module(APPLICATION_PROPERTIES_COMPONENT, [
])
  .component('applicationFastProperties', react2angular(ApplicationProperties, ['application']));
