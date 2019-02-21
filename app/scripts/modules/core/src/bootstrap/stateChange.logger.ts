import { ILogService } from 'angular';

import { UIRouter, Transition } from '@uirouter/core';
import '@uirouter/rx';

import { bootstrapModule } from './bootstrap.module';

bootstrapModule.run([
  '$uiRouter',
  '$log',
  ($uiRouter: UIRouter, $log: ILogService) => {
    'ngInject';

    const subscription = $uiRouter.globals.start$.subscribe((transition: Transition) => {
      const details = {
        transition,
        toState: transition.to(),
        toParams: transition.params('to'),
        fromState: transition.from(),
        fromParams: transition.params('from'),
      };

      $log.debug('$stateChangeStart', details);
      const success = () => $log.debug('$stateChangeSuccess', details);
      const failure = (error: any) => $log.debug('$stateChangeError', { ...details, error });

      transition.promise.then(success, failure);
    });

    $uiRouter.disposable({ dispose: () => subscription.unsubscribe() });
  },
]);
