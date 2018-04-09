import { module } from 'angular';
import { IModalInstanceService, IModalService } from 'angular-ui-bootstrap';

export const UIB_MODAL_REJECTIONS = 'spinnaker.core.utils.uibModalRejections';
const mod = module(UIB_MODAL_REJECTIONS, []);

// Avoid "Possibly unhandled rejection" in console when closing a uibModal
mod.decorator('$uibModal', function($delegate: IModalService) {
  const realOpen = $delegate.open;
  $delegate.open = function() {
    const modalInstance: IModalInstanceService = realOpen.apply(this, arguments);
    modalInstance.result.catch(() => {});
    return modalInstance;
  };
  return $delegate;
});
