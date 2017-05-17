import { module } from 'angular';

import { VERSION_CHECK_SERVICE } from './versionCheck.service';

export const CONFIG_MODULE = 'spinnaker.core.config';
module(CONFIG_MODULE, [
  VERSION_CHECK_SERVICE,
]);
