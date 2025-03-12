import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { AmazonInstanceInformation } from './AmazonInstanceInformation';

export const AMAZON_INSTANCE_INFORMATION_COMPONENT = 'spinnaker.application.amazonInstanceInformation.component';

module(AMAZON_INSTANCE_INFORMATION_COMPONENT, []).component(
  'amazonInstanceInformation',
  react2angular(withErrorBoundary(AmazonInstanceInformation, 'amazonInstanceInformation'), ['instance']),
);
