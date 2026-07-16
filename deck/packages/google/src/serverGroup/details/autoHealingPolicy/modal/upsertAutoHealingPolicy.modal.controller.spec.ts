'use strict';

describe('Controller: gceUpsertAutoHealingPolicyModalCtrl', function () {
  beforeEach(
    window.module(require('./upsertAutoHealingPolicy.modal.controller').GCE_UPSERT_AUTOHEALING_POLICY_MODAL_CTRL),
  );

  beforeEach(
    window.inject(function ($controller, $q) {
      this.$controller = $controller;
      this.$q = $q;
    }),
  );

  it('strips legacy maxUnavailable when editing an existing policy', function () {
    const ctrl = this.$controller('gceUpsertAutoHealingPolicyModalCtrl', {
      $uibModalInstance: {
        dismiss: angular.noop,
        // TaskMonitor binds to modalInstance.result.then during construction.
        result: this.$q.defer().promise,
      },
      application: { name: 'myapp' },
      serverGroup: {
        name: 'myapp-main-v000',
        account: 'test-account',
        autoHealingPolicy: {
          healthCheck: 'hc',
          initialDelaySec: 45,
          maxUnavailable: { percent: 10 },
        },
      },
      gceHealthCheckReader: { listHealthChecks: () => this.$q.when([]) },
      gceAutoscalingPolicyWriter: {},
    });

    expect(ctrl.autoHealingPolicy).toEqual({
      healthCheck: 'hc',
      initialDelaySec: 45,
    });
    expect(ctrl.autoHealingPolicy.maxUnavailable).toBeUndefined();
  });
});
