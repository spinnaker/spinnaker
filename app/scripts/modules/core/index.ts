import {IDowngradeItem} from 'core/domain/IDowngradeItem';
import {AUTHENTICATION_MODULE_DOWNGRADES} from './authentication';
import {UPGRADE_MODULE_DOWNGRADES} from './upgrade';
import {MODAL_COMPONENT_MODULE_DOWNGRADES} from './modal';

export const CORE_MODULE_DOWNGRADES: IDowngradeItem[] = [
  ...AUTHENTICATION_MODULE_DOWNGRADES,
  ...UPGRADE_MODULE_DOWNGRADES
];

export const CORE_COMPONENT_MODULE_DOWNGRADES: IDowngradeItem[] = [
  ... MODAL_COMPONENT_MODULE_DOWNGRADES
];
