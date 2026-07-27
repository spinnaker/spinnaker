import { registerApplicationInitializer } from '@spinnaker/core';

import { initializeKayenta } from './initializeKayenta';

registerApplicationInitializer(initializeKayenta);
