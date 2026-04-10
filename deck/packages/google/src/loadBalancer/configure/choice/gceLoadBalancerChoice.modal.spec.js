import { GCE_LOAD_BALANCER_CHOICE_MODAL } from './gceLoadBalancerChoice.modal';

describe('Controller: gceLoadBalancerChoiceCtrl', () => {
  beforeEach(() => {
    window.module(GCE_LOAD_BALANCER_CHOICE_MODAL);
  });

  beforeEach(
    window.inject(function ($controller, $rootScope, $q) {
      this.$scope = $rootScope.$new();
      this.modalInstance = {
        close: jasmine.createSpy('close'),
        dismiss: jasmine.createSpy('dismiss'),
      };
      this.wizardResult = $q.when('wizard');
      this.$uibModal = {
        open: jasmine.createSpy('open').and.returnValue({ result: this.wizardResult }),
      };

      this.ctrl = $controller('gceLoadBalancerChoiceCtrl', {
        $scope: this.$scope,
        $uibModal: this.$uibModal,
        $uibModalInstance: this.modalInstance,
        application: { name: 'app' },
        loadBalancerTypeToWizardMap: {
          NETWORK: { label: 'Network', createTemplateUrl: 'template', controller: 'ctrl' },
        },
        forPipelineConfig: true,
      });
    }),
  );

  it('closes with wizard result and forwards forPipelineConfig', function () {
    this.ctrl.choose('Network');

    const modalArgs = this.$uibModal.open.calls.mostRecent().args[0];
    expect(modalArgs.resolve.forPipelineConfig()).toBe(true);
    expect(this.modalInstance.close).toHaveBeenCalledWith(this.wizardResult);
    expect(this.modalInstance.dismiss).not.toHaveBeenCalled();
  });
});
