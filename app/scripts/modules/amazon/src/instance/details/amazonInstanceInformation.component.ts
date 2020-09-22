import { module } from 'angular';
import { react2angular } from 'react2angular';

import { AmazonInstanceInformation } from './AmazonInstanceInformation';

export const AMAZON_INSTANCE_INFORMATION_COMPONENT = 'spinnaker.application.amazonInstanceInformation.component';

module(AMAZON_INSTANCE_INFORMATION_COMPONENT, []).component(
  'amazonInstanceInformation',
  react2angular(AmazonInstanceInformation, ['instance']),
);
