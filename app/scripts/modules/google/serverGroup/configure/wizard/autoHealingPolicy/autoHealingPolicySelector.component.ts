import {set} from 'lodash';
import {module} from 'angular';

class GceAutoHealingPolicySelector implements ng.IComponentController {
  public command: any;

  static get $inject() { return ['gceServerGroupConfigurationService']; }

  constructor(private gceServerGroupConfigurationService: any) {}

  public setAutoHealing(): void {
    if (this.command.enableAutoHealing) {
      this.command.autoHealingPolicy = {initialDelaySec: 300};
    } else {
      this.command.autoHealingPolicy = {};
    }
  }

  public manageMaxUnavailableMetric(selectedMetric: string): void {
    if (!selectedMetric) {
      delete this.command.autoHealingPolicy.maxUnavailable;
    } else {
      let toDeleteKey = selectedMetric === 'percent' ? 'fixed' : 'percent';
      set(this.command.autoHealingPolicy, ['maxUnavailable', toDeleteKey], undefined);
    }
  }

  public onHealthCheckRefresh(): void {
    this.gceServerGroupConfigurationService.refreshHttpHealthChecks(this.command);
  }
}

class GceAutoHealingPolicySelectorComponent implements ng.IComponentOptions {
  public bindings: any = {
    command: '=',
  };
  public templateUrl: string = require('./autoHealingPolicySelector.component.html');
  public controller: any = GceAutoHealingPolicySelector;
}

export const GCE_AUTOHEALING_POLICY_SELECTOR = 'spinnaker.gce.autoHealingPolicy.selector.component';

module(GCE_AUTOHEALING_POLICY_SELECTOR, [
  require('../../serverGroupConfiguration.service.js'),
]).component('gceAutoHealingPolicySelector', new GceAutoHealingPolicySelectorComponent());

