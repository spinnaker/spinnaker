import { IComponentOptions, IController, module } from 'angular';
import { set } from 'lodash';

import { IGceAutoHealingPolicy } from '../../../../domain/autoHealingPolicy';
import { IGceHealthCheckOption, parseHealthCheckUrl } from '../../../../healthCheck/healthCheckUtils';

class GceAutoHealingPolicySelector implements IController {
  public healthChecks: string[];
  public autoHealingPolicy: IGceAutoHealingPolicy;
  public enabled: boolean;
  public viewState: { maxUnavailableMetric: 'percent' | 'fixed' };
  private setAutoHealingPolicy: Function;

  public $onInit(): void {
    if (this.autoHealingPolicy && this.autoHealingPolicy.maxUnavailable) {
      if (typeof this.autoHealingPolicy.maxUnavailable.fixed === 'number') {
        this.viewState = { maxUnavailableMetric: 'fixed' };
      } else if (typeof this.autoHealingPolicy.maxUnavailable.percent === 'number') {
        this.viewState = { maxUnavailableMetric: 'percent' };
      }
    }

    if (!this.autoHealingPolicy) {
      this.setAutoHealingPolicy({ autoHealingPolicy: { initialDelaySec: 300 } });
    }
  }

  public $onDestroy(): void {
    this.setAutoHealingPolicy({ autoHealingPolicy: null });
  }

  public manageMaxUnavailableMetric(selectedMetric: string): void {
    if (!selectedMetric) {
      // Clouddriver deletes maxUnavailable if maxUnavailable is an empty object.
      this.autoHealingPolicy.maxUnavailable = {};
    } else {
      const toDeleteKey = selectedMetric === 'percent' ? 'fixed' : 'percent';
      set(this.autoHealingPolicy, ['maxUnavailable', toDeleteKey], undefined);
    }
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
