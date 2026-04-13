import { GCE_LOAD_BALANCER_CHOICE_MODAL } from './gceLoadBalancerChoice.modal';

describe('Controller: gceLoadBalancerChoiceCtrl', () => {
  beforeEach(() => {
    window.module(GCE_LOAD_BALANCER_CHOICE_MODAL);
  });

  const buildController = (overrides = {}) => {
    let result;
    window.inject(function ($controller, $rootScope, $q) {
      const $scope = $rootScope.$new();
      const modalInstance = {
        close: jasmine.createSpy('close'),
        dismiss: jasmine.createSpy('dismiss'),
      };
      const wizardResult = $q.when('wizard');
      const $uibModal = {
        open: jasmine.createSpy('open').and.returnValue({ result: wizardResult }),
      };
      const ctrl = $controller('gceLoadBalancerChoiceCtrl', {
        $scope,
        $uibModal,
        $uibModalInstance: modalInstance,
        application: { name: 'app' },
        loadBalancerTypeToWizardMap: {
          NETWORK: { label: 'Network', createTemplateUrl: 'template', controller: 'ctrl' },
        },
        forPipelineConfig: true,
        ...overrides,
      });

      result = { ctrl, $uibModal, modalInstance, wizardResult };
    });

    return result;
  };

  it('closes with wizard config in pipeline mode', function () {
    const { ctrl, $uibModal, modalInstance } = buildController();

    ctrl.choose('Network');

    expect($uibModal.open).not.toHaveBeenCalled();
    expect(modalInstance.close).toHaveBeenCalledWith(jasmine.objectContaining({ controller: 'ctrl' }));
    expect(modalInstance.dismiss).not.toHaveBeenCalled();
  });

  it('opens wizard in non-pipeline mode', function () {
    const { ctrl, $uibModal, modalInstance, wizardResult } = buildController({ forPipelineConfig: false });

    ctrl.choose('Network');

    expect($uibModal.open).toHaveBeenCalled();
    expect(modalInstance.close).toHaveBeenCalledWith(wizardResult);
  });

  it('dismisses when choice has no wizard config', function () {
    const { ctrl, $uibModal, modalInstance } = buildController();

    ctrl.choose('Missing');

    expect($uibModal.open).not.toHaveBeenCalled();
    expect(modalInstance.close).not.toHaveBeenCalled();
    expect(modalInstance.dismiss).toHaveBeenCalled();
  });
});
