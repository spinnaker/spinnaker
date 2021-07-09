import { module } from 'angular';
import { react2angular } from 'react2angular';

import { InstanceDetailsHeader } from './InstanceDetailsHeader';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const CORE_INSTANCE_DETAILS_HEADER_COMPONENT = 'spinnaker.core.instance.details.header';
export const name = CORE_INSTANCE_DETAILS_HEADER_COMPONENT;

module(name, []).component(
  'instanceDetailsHeader',
  react2angular(withErrorBoundary(InstanceDetailsHeader, 'instanceDetailsHeader'), [
    'cloudProvider',
    'healthState',
    'instanceId',
    'loading',
    'sshLink',
    'standalone',
  ]),
);
