import { module } from 'angular';
import { IModalInstanceService, IModalService } from 'angular-ui-bootstrap';

export const UIB_MODAL_REJECTIONS = 'spinnaker.core.utils.uibModalRejections';

// Avoid "Possibly unhandled rejection" in console when closing a uibModal
module(UIB_MODAL_REJECTIONS, []).decorator('$uibModal', [
  '$delegate',
  function ($delegate: IModalService) {
    const realOpen = $delegate.open;
    $delegate.open = function () {
      const modalInstance: IModalInstanceService = realOpen.apply(this, arguments);
      modalInstance.result.catch(() => {});
      return modalInstance;
    };
    return $delegate;
  },
]);
