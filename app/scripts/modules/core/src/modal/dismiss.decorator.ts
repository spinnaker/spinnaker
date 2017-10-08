import { module } from 'angular';
import IProvideService = angular.auto.IProvideService;

import { IModalStackService } from 'angular-ui-bootstrap';

export const DISMISS_DECORATOR = 'spinnaker.core.modal.dismiss.decorator';
module(DISMISS_DECORATOR, [])
  .config(($provide: IProvideService) => {
    // Prevents an error from escaping when the user dismisses a modal created via the ReactInjector
    // (error looks like: "Possibly unhandled rejection: undefined undefined")
    $provide.decorator('$uibModalStack', ($delegate: IModalStackService) => {
      const originalDismiss: Function = $delegate.dismiss;
      $delegate.dismiss = (...args: any[]) => {
        originalDismiss.apply(this, args);
        const [ modalInstance ] = args;
        if (modalInstance && modalInstance.result) {
          modalInstance.result
            .catch(() => {});
        }
      };

      return $delegate;
    });
  });
