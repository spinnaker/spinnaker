import {Type} from '@angular/core';

import {IDowngradeItem} from 'core/domain/IDowngradeItem';
import {AUTHENTICATION_MODULE_DOWNGRADES} from './authentication';
import {HELP_DIRECTIVE_UPGRADES} from './help';
import {MODAL_COMPONENT_MODULE_DOWNGRADES} from './modal';
import {UPGRADE_MODULE_DOWNGRADES} from './upgrade';
import {WIDGET_DIRECTIVE_UPGRADES} from './widgets';

export const CORE_MODULE_DOWNGRADES: IDowngradeItem[] = [
  ...AUTHENTICATION_MODULE_DOWNGRADES,
  ...UPGRADE_MODULE_DOWNGRADES
];

export const CORE_COMPONENT_MODULE_DOWNGRADES: IDowngradeItem[] = [
  ...MODAL_COMPONENT_MODULE_DOWNGRADES
];

export const CORE_DIRECTIVE_UPGRADES: Type<any>[] = [
  ...HELP_DIRECTIVE_UPGRADES,
  ...WIDGET_DIRECTIVE_UPGRADES
];
