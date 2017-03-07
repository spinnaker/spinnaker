import {IDowngradeItem} from '../domain/IDowngradeItem';

import {AUTHENTICATION_SERVICE_DOWNGRADE} from './authentication.service';

export const AUTHENTICATION_MODULE_DOWNGRADES: IDowngradeItem[] = [
  AUTHENTICATION_SERVICE_DOWNGRADE
];
