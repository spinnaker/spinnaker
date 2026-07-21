import { module } from 'angular';

import { InstanceDetailsHeader } from './InstanceDetailsHeader';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const CORE_INSTANCE_DETAILS_HEADER_COMPONENT = 'spinnaker.core.instance.details.header';
export const name = CORE_INSTANCE_DETAILS_HEADER_COMPONENT;

module(name, []).component(
  'instanceDetailsHeader',
  angularComponentFromReact(InstanceDetailsHeader, 'instanceDetailsHeader', [
    'cloudProvider',
    'healthState',
    'instanceId',
    'loading',
    'sshLink',
    'standalone',
  ]),
);
