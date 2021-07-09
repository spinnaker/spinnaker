import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { InstanceDns } from './InstanceDns';

export const INSTANCE_DNS_COMPONENT = 'spinnaker.application.instanceDns.component';

module(INSTANCE_DNS_COMPONENT, []).component(
  'instanceDns',
  react2angular(withErrorBoundary(InstanceDns, 'instanceDns'), [
    'instancePort',
    'ipv6Addresses',
    'permanentIps',
    'privateDnsName',
    'privateIpAddress',
    'publicDnsName',
    'publicIpAddress',
  ]),
);
