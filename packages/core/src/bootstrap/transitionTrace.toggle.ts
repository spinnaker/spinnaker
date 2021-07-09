import { Category, UIRouter } from '@uirouter/core';

import { bootstrapModule } from './bootstrap.module';
import { paramChangedHelper } from './paramChangedHelper';

/** Changes UI-Router console tracing based on the query parameter `trace` */
bootstrapModule.run([
  '$uiRouter',
  ($uiRouter: UIRouter) => {
    const changeTraceSetting = (newValue: string) => {
      const trace = $uiRouter.trace;
      trace.disable();
      if (typeof newValue === 'string') {
        if (newValue.toUpperCase() === 'TRUE') {
          trace.enable(Category.TRANSITION);
        } else if (newValue.toUpperCase() === 'ALL') {
          trace.enable();
        } else {
          const traceValues = newValue.split(',').map((str) => str.trim().toUpperCase());
          trace.enable(...(traceValues as any));
        }
      }
    };

    $uiRouter.transitionService.onBefore({}, paramChangedHelper('trace', changeTraceSetting));
  },
]);
