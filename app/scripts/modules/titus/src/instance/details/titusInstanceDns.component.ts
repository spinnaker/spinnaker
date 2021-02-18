import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { TitusInstanceDns } from './TitusInstanceDns';

export const TITUS_INSTANCE_DNS_COMPONENT = 'spinnaker.application.titusInstanceDns.component';

module(TITUS_INSTANCE_DNS_COMPONENT, []).component(
  'titusInstanceDns',
  react2angular(withErrorBoundary(TitusInstanceDns, 'titusInstanceInformation'), [
    'containerIp',
    'host',
    'instancePort',
    'ipv6Address',
  ]),
);
