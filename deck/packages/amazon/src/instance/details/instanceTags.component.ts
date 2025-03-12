import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { InstanceTags } from './InstanceTags';

export const INSTANCE_TAGS_COMPONENT = 'spinnaker.application.instanceTags.component';

module(INSTANCE_TAGS_COMPONENT, []).component(
  'instanceTags',
  react2angular(withErrorBoundary(InstanceTags, 'instanceTags'), ['tags']),
);
