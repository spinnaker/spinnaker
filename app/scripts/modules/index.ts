import {IDowngradeItem} from 'core/domain/IDowngradeItem';
import {CORE_MODULE_DOWNGRADES, CORE_COMPONENT_MODULE_DOWNGRADES} from './core';

export const SPINNAKER_DOWNGRADES: IDowngradeItem[] = [
  ...CORE_MODULE_DOWNGRADES
];

export const SPINNAKER_COMPONENT_DOWNGRADES: IDowngradeItem[] = [
  ...CORE_COMPONENT_MODULE_DOWNGRADES
];
