import type { IComponentOptions, IController } from 'angular';
import { module } from 'angular';

import type { IGceAutoHealingPolicy } from '../../../../domain/autoHealingPolicy';
import type { IGceHealthCheckOption } from '../../../../healthCheck/healthCheckUtils';
import { parseHealthCheckUrl } from '../../../../healthCheck/healthCheckUtils';

class GceAutoHealingPolicySelector implements IController {
  public healthChecks: string[];
  public autoHealingPolicy: IGceAutoHealingPolicy;
  public enabled: boolean;
  private setAutoHealingPolicy: Function;

  public $onInit(): void {
    if (!this.autoHealingPolicy) {
      this.setAutoHealingPolicy({ autoHealingPolicy: { initialDelaySec: 300 } });
    }
  }

  public $onDestroy(): void {
    this.setAutoHealingPolicy({ autoHealingPolicy: null });
  }

  public onHealthCheckChange(_healthCheck: IGceHealthCheckOption, healthCheckUrl: string) {
    if (healthCheckUrl) {
      const { healthCheckName, healthCheckKind } = parseHealthCheckUrl(healthCheckUrl);
      this.autoHealingPolicy.healthCheck = healthCheckName;
      this.autoHealingPolicy.healthCheckKind = healthCheckKind;
    }
  }
}

const gceAutoHealingPolicySelectorComponent: IComponentOptions = {
  bindings: {
    onHealthCheckRefresh: '&',
    setAutoHealingPolicy: '&',
    healthChecks: '<',
    autoHealingPolicy: '<',
    enabled: '<',
    labelColumns: '@?',
  },
  templateUrl: require('./autoHealingPolicySelector.component.html'),
  controller: GceAutoHealingPolicySelector,
};

export const GCE_AUTOHEALING_POLICY_SELECTOR = 'spinnaker.gce.autoHealingPolicy.selector.component';
module(GCE_AUTOHEALING_POLICY_SELECTOR, []).component(
  'gceAutoHealingPolicySelector',
  gceAutoHealingPolicySelectorComponent,
);
