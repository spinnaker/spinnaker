'use strict';

import { AUTHENTICATION_USER_MENU } from './userMenu.directive';

import './userMenu.less';

import { module } from 'angular';

export const CORE_AUTHENTICATION_USERMENU_USERMENU_MODULE = 'spinnaker.core.authentication.userMenu';
export const name = CORE_AUTHENTICATION_USERMENU_USERMENU_MODULE; // for backwards compatibility
module(CORE_AUTHENTICATION_USERMENU_USERMENU_MODULE, [AUTHENTICATION_USER_MENU]);
