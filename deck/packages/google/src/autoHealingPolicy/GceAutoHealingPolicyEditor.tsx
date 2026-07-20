import React from 'react';

import type { IGceAutoHealingPolicy } from '../domain';
import { GceHealthCheckReader } from '../healthCheck/healthCheck.read.service';
import type { IGceHealthCheckOption } from '../healthCheck/healthCheckUtils';
import { getHealthCheckOptions, parseHealthCheckUrl } from '../healthCheck/healthCheckUtils';

export interface IGceAutoHealingPolicyEditorProps {
  account: string;
  policy: IGceAutoHealingPolicy;
  onChange: (policy: IGceAutoHealingPolicy) => void;
  reader?: GceHealthCheckReader;
}

interface IGceAutoHealingPolicyEditorState {
  healthChecks: IGceHealthCheckOption[];
  loading: boolean;
}

function numberValue(value: string): number | undefined {
  return value === '' ? undefined : Number(value);
}

export class GceAutoHealingPolicyEditor extends React.Component<
  IGceAutoHealingPolicyEditorProps,
  IGceAutoHealingPolicyEditorState
> {
  public state: IGceAutoHealingPolicyEditorState = { healthChecks: [], loading: true };

  public componentDidMount(): void {
    this.loadHealthChecks();
  }

  public loadHealthChecks = (): void => {
    this.setState({ loading: true });
    (this.props.reader || new GceHealthCheckReader()).listHealthChecks().then((healthChecks) => {
      const options = getHealthCheckOptions(
        healthChecks.filter((healthCheck) => healthCheck.account === this.props.account),
      );
      this.setState({
        healthChecks: options,
        loading: false,
      });
      this.normalizeHealthCheck(options);
    });
  };

  private update = (changes: Partial<IGceAutoHealingPolicy>): void => {
    this.props.onChange({ ...this.props.policy, ...changes });
  };

  private getHealthCheckUrl = (healthChecks = this.state.healthChecks): string => {
    const { healthCheck, healthCheckKind, healthCheckUrl } = this.props.policy;
    if (healthCheckUrl) {
      return healthCheckUrl;
    }
    if (healthCheck?.includes('/')) {
      return healthCheck;
    }
    const matches = healthChecks.filter(
      (option) => option.name === healthCheck && (!healthCheckKind || option.kind === healthCheckKind),
    );
    return matches.length === 1 ? matches[0].selfLink : '';
  };

  private normalizeHealthCheck(healthChecks: IGceHealthCheckOption[]): void {
    const healthCheckUrl = this.getHealthCheckUrl(healthChecks);
    if (!healthCheckUrl) {
      return;
    }
    const { healthCheckName, healthCheckKind } = parseHealthCheckUrl(healthCheckUrl);
    if (
      this.props.policy.healthCheckUrl !== healthCheckUrl ||
      this.props.policy.healthCheck !== healthCheckName ||
      this.props.policy.healthCheckKind !== healthCheckKind
    ) {
      this.update({ healthCheckUrl, healthCheck: healthCheckName, healthCheckKind });
    }
  }

  public render(): JSX.Element {
    const { policy } = this.props;

    return (
      <div className="form-horizontal gce-auto-healing-policy-editor">
        <div className="form-group">
          <label className="col-md-4 control-label">Health check</label>
          <div className="col-md-6">
            <select
              className="form-control input-sm"
              data-testid="health-check"
              disabled={this.state.loading}
              value={this.getHealthCheckUrl()}
              onChange={(event) => {
                const healthCheckUrl = event.target.value;
                if (!healthCheckUrl) {
                  this.update({ healthCheckUrl: undefined, healthCheck: undefined, healthCheckKind: undefined });
                  return;
                }
                const { healthCheckName, healthCheckKind } = parseHealthCheckUrl(healthCheckUrl);
                this.update({ healthCheckUrl, healthCheck: healthCheckName, healthCheckKind });
              }}
            >
              <option value="">Select...</option>
              {this.state.healthChecks.map((healthCheck) => (
                <option key={healthCheck.selfLink} value={healthCheck.selfLink}>
                  {healthCheck.displayName}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="form-group">
          <label className="col-md-4 control-label">Initial delay</label>
          <div className="col-md-6 input-group">
            <input
              className="form-control input-sm"
              data-testid="initial-delay"
              max={2147483647}
              min={0}
              type="number"
              value={policy.initialDelaySec ?? ''}
              onChange={(event) => this.update({ initialDelaySec: numberValue(event.target.value) })}
            />
            <span className="input-group-addon">seconds</span>
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-offset-4 col-md-6">
            <button
              className="btn btn-link"
              disabled={this.state.loading}
              type="button"
              onClick={this.loadHealthChecks}
            >
              Refresh health checks
            </button>
          </div>
        </div>
      </div>
    );
  }
}
