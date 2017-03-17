import {Type} from '@angular/core';

import {IDowngradeItem} from 'core/domain/IDowngradeItem';
import {CORE_MODULE_DOWNGRADES, CORE_COMPONENT_MODULE_DOWNGRADES, CORE_DIRECTIVE_UPGRADES} from './core';
import {NETFLIX_COMPONENT_MODULE_DOWNGRADES} from './netflix';

export const SPINNAKER_DOWNGRADES: IDowngradeItem[] = [
  ...CORE_MODULE_DOWNGRADES
];

export const SPINNAKER_COMPONENT_DOWNGRADES: IDowngradeItem[] = [
  ...CORE_COMPONENT_MODULE_DOWNGRADES,
  ...NETFLIX_COMPONENT_MODULE_DOWNGRADES
];

export const SPINNAKER_DIRECTIVE_UPGRADES: Type<any>[] = [
  ...CORE_DIRECTIVE_UPGRADES
];
