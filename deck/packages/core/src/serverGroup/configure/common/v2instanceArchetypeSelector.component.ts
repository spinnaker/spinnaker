import type { IComponentOptions } from 'angular';
import { module } from 'angular';

import { InstanceArchetypeSelector } from './InstanceArchetypeSelector';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const v2InstanceArchetypeSelector: IComponentOptions = angularComponentFromReact(
  InstanceArchetypeSelector,
  'v2InstanceArchetypeSelector',
  ['command', 'onProfileChanged', 'onTypeChanged'],
);

export const V2_INSTANCE_ARCHETYPE_SELECTOR = 'spinnaker.core.serverGroup.configure.common.v2instanceArchetypeSelector';
module(V2_INSTANCE_ARCHETYPE_SELECTOR, []).component('v2InstanceArchetypeSelector', v2InstanceArchetypeSelector);
