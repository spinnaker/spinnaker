import { module } from 'angular';

import { PermissionsConfigurer } from './PermissionsConfigurer';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const PERMISSIONS_CONFIGURER_COMPONENT = 'spinnaker.application.permissionsConfigurer.component';
module(PERMISSIONS_CONFIGURER_COMPONENT, []).component(
  'permissionsConfigurer',
  angularComponentFromReact(PermissionsConfigurer, 'permissionsConfigurer', [
    'permissions',
    'requiredGroupMembership',
    'onPermissionsChange',
  ]),
);
