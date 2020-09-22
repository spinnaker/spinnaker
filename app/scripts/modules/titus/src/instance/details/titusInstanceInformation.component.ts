import { module } from 'angular';
import { react2angular } from 'react2angular';

import { TitusInstanceInformation } from './TitusInstanceInformation';

export const TITUS_INSTANCE_INFORMATION_COMPONENT = 'spinnaker.application.titusInstanceInformation.component';

module(TITUS_INSTANCE_INFORMATION_COMPONENT, []).component(
  'titusInstanceInformation',
  react2angular(TitusInstanceInformation, ['instance', 'titusUiEndpoint']),
);
