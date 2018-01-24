import * as React from 'react';
import { set } from 'lodash';
import { BindAll } from 'lodash-decorators';
import { FormikErrors, FormikProps } from 'formik';
import { Observable, Subject } from 'rxjs';

import { Application, HelpField, IWizardPageProps, ValidationError, wizardPage } from '@spinnaker/core';

import {
  IAmazonApplicationLoadBalancer,
  IAmazonApplicationLoadBalancerUpsertCommand,
} from 'amazon/domain';

export interface ITargetGroupsProps {
  app: Application;
  loadBalancer: IAmazonApplicationLoadBalancer;
}

export interface ITargetGroupsState {
  existingTargetGroupNames: string[];
}

@BindAll()
class TargetGroupsImpl extends React.Component<ITargetGroupsProps & IWizardPageProps & FormikProps<IAmazonApplicationLoadBalancerUpsertCommand>, ITargetGroupsState> {
  public static LABEL = 'Target Groups';

  public protocols = ['HTTP', 'HTTPS'];
  private destroy$ = new Subject();

  constructor(props: ITargetGroupsProps & IWizardPageProps & FormikProps<IAmazonApplicationLoadBalancerUpsertCommand>) {
    super(props);
    this.state = {
      existingTargetGroupNames: [],
    };
  }

  public validate(values: IAmazonApplicationLoadBalancerUpsertCommand): FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand> {
    const errors = {} as FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand>;

    let hasErrors = false;
    const targetGroupsErrors = values.targetGroups.map((targetGroup: any) => {
      const tgErrors: { [key: string]: string } = {};

      if (targetGroup.name && this.state.existingTargetGroupNames.includes(targetGroup.name.toLowerCase())) {
        tgErrors.name = `There is already a target group in ${values.credentials}:${values.region} with that name.`;
      }

      if (targetGroup.name && targetGroup.name.length > 32 - this.props.app.name.length) {
        tgErrors.name = 'Target group name is automatically prefixed with the application name and cannot exceed 32 characters in length.';
      }

      ['name', 'protocol', 'port', 'healthCheckInterval', 'healthCheckPath', 'healthCheckPort', 'healthCheckProtocol'].forEach((key) => {
        if (!targetGroup[key]) { tgErrors[key] = 'Required'; }
      })

      if (Object.keys(tgErrors).length > 0) { hasErrors = true; }
      return tgErrors;
    });

    if (hasErrors) { errors.targetGroups = targetGroupsErrors; }
    return errors;
  }

  private removeAppName(name: string): string {
    return name.replace(`${this.props.app.name}-`, '');
  }

  protected updateLoadBalancerNames(): void {
    const { app, loadBalancer, values: { credentials: account, region } } = this.props;

    const accountLoadBalancersByRegion: { [region: string]: string[] } = {};
    const accountTargetGroupsByRegion: { [region: string]: string[] } = {};
    Observable.fromPromise(app.getDataSource('loadBalancers').refresh(true))
      .takeUntil(this.destroy$)
      .subscribe(() => {
        app.getDataSource('loadBalancers').data.forEach((lb: IAmazonApplicationLoadBalancer) => {
          if (lb.account === account) {
            accountLoadBalancersByRegion[lb.region] = accountLoadBalancersByRegion[lb.region] || [];
            accountLoadBalancersByRegion[lb.region].push(lb.name);

            if (lb.loadBalancerType === 'application') {
              if (!loadBalancer || lb.name !== loadBalancer.name) {
                lb.targetGroups.forEach((targetGroup) => {
                  accountTargetGroupsByRegion[lb.region] = accountTargetGroupsByRegion[lb.region] ||  [];
                  accountTargetGroupsByRegion[lb.region].push(this.removeAppName(targetGroup.name));
                });
              }
            }
          }
        });

      this.setState({ existingTargetGroupNames: accountTargetGroupsByRegion[region] || [] });
    });
  }

  private targetGroupFieldChanged(index: number, field: string, value: string | boolean): void {
    const { setFieldValue, values } = this.props;
    const targetGroup = values.targetGroups[index];
    set(targetGroup, field, value);
    setFieldValue('targetGroups', values.targetGroups);
  }

  private addTargetGroup(): void {
    const { setFieldValue, values } = this.props;
    const tgLength = values.targetGroups.length;
    values.targetGroups.push({
      name: `targetgroup${tgLength ? `${tgLength}` : ''}`,
      protocol: 'HTTP',
      port: 7001,
      healthCheckProtocol: 'HTTP',
      healthCheckPort: '7001',
      healthCheckPath: '/healthcheck',
      healthCheckTimeout: 5,
      healthCheckInterval: 10,
      healthyThreshold: 10,
      unhealthyThreshold: 2,
      attributes: {
        deregistrationDelay: 600,
        stickinessEnabled: false,
        stickinessType: 'lb_cookie',
        stickinessDuration: 8400
      }
    });
    setFieldValue('targetGroups', values.targetGroups);
  }

  private removeTargetGroup(index: number): void {
    const { setFieldValue, values } = this.props;
    values.targetGroups.splice(index, 1);
    setFieldValue('targetGroups', values.targetGroups);
  }

  public componentDidMount(): void {
    this.updateLoadBalancerNames();
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public render() {
    const { app, errors, values } = this.props;

    const ProtocolOptions = this.protocols.map((p) => <option key={p}>{p}</option>);

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-12">
            {values.targetGroups.map((targetGroup, index) => (
              <div key={index} className="wizard-pod">
                <div>
                  <div className="wizard-pod-row header">
                    <div className="wizard-pod-row-title">Group Name</div>
                    <div className="wizard-pod-row-contents">
                      <div className="wizard-pod-row-data">
                        <span className="group-name-prefix">{app.name}-</span>
                        <input
                          className="form-control input-sm target-group-name"
                          type="text"
                          value={targetGroup.name}
                          onChange={(event) => this.targetGroupFieldChanged(index, 'name', event.target.value)}
                          required={true}
                        />
                        <a className="sm-label clickable" onClick={() => this.removeTargetGroup(index)}><span className="glyphicon glyphicon-trash"/></a>
                      </div>
                      {errors.targetGroups && errors.targetGroups[index].name && <div className="wizard-pod-row-errors"><ValidationError message={errors.targetGroups[index].name}/></div>}
                    </div>
                  </div>
                  <div className="wizard-pod-row">
                    <div className="wizard-pod-row-title">Backend Connection</div>
                    <div className="wizard-pod-row-contents">
                      <div className="wizard-pod-row-data">
                        <span className="wizard-pod-content">
                          <label>Protocol </label>
                          <select
                            className="form-control input-sm inline-number"
                            value={targetGroup.protocol}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'protocol', event.target.value)}
                          >
                            {ProtocolOptions}
                          </select>
                        </span>
                        <span className="wizard-pod-content">
                          <label>Port </label>
                          <input
                            className="form-control input-sm inline-number"
                            value={targetGroup.port}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'port', event.target.value)}
                            type="text"
                            required={true}
                          />
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="wizard-pod-row">
                    <div className="wizard-pod-row-title">Healthcheck</div>
                    <div className="wizard-pod-row-contents">
                      <div className="wizard-pod-row-data">
                        <span className="wizard-pod-content">
                          <label>Protocol </label>
                          <select
                            className="form-control input-sm inline-number"
                            value={targetGroup.healthCheckProtocol}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'healthCheckProtocol', event.target.value)}
                          >
                            {ProtocolOptions}
                          </select>
                        </span>
                        <span className="wizard-pod-content">
                          <label>Port </label>
                          <input
                            className="form-control input-sm inline-number"
                            type="text"
                            required={true}
                            value={targetGroup.healthCheckPort}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'healthCheckPort', event.target.value)}
                          />
                        </span>
                        <span className="wizard-pod-content">
                          <label>Path </label>
                          <input
                            className="form-control input-sm inline-text"
                            type="text"
                            required={true}
                            value={targetGroup.healthCheckPath}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'healthCheckPath', event.target.value)}
                          />
                        </span>
                        <span className="wizard-pod-content">
                          <label>Timeout </label>
                          <input
                            className="form-control input-sm inline-number"
                            type="text"
                            required={true}
                            value={targetGroup.healthCheckTimeout}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'healthCheckTimeout', event.target.value)}
                          />
                        </span>
                        <span className="wizard-pod-content">
                          <label>Interval </label>
                          <input
                            className="form-control input-sm inline-number"
                            type="text"
                            required={true}
                            value={targetGroup.healthCheckInterval}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'healthCheckInterval', event.target.value)}
                          />
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="wizard-pod-row">
                    <div className="wizard-pod-row-title">Healthcheck Threshold</div>
                    <div className="wizard-pod-row-contents">
                      <div className="wizard-pod-row-data">
                        <span className="wizard-pod-content">
                          <label>Healthy </label>
                          <input
                            className="form-control input-sm inline-number"
                            type="number"
                            required={true}
                            value={targetGroup.healthyThreshold}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'healthyThreshold', event.target.value)}
                          />
                        </span>
                        <span className="wizard-pod-content">
                          <label>Unhealthy </label>
                          <input
                            className="form-control input-sm inline-number"
                            type="number"
                            required={true}
                            value={targetGroup.unhealthyThreshold}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'unhealthyThreshold', event.target.value)}
                          />
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="wizard-pod-row">
                    <div className="wizard-pod-row-title">Attributes</div>
                    <div className="wizard-pod-row-contents">
                      <div className="wizard-pod-row-data">
                        <span className="wizard-pod-content">
                          <label>Dereg. Delay</label><HelpField id="aws.targetGroup.attributes.deregistrationDelay"/>{' '}
                          <input
                            className="form-control input-sm inline-number"
                            type="number"
                            value={targetGroup.attributes.deregistrationDelay}
                            onChange={(event) => this.targetGroupFieldChanged(index, 'attributes.deregistrationDelay', event.target.value)}
                          />
                        </span>
                        <span className="wizard-pod-content">
                          <label className="checkbox-inline" style={{ paddingTop: '2px' }}>
                            <input
                              type="checkbox"
                              value={targetGroup.attributes.stickinessEnabled ? 'true' : 'false'}
                              onChange={(event) => this.targetGroupFieldChanged(index, 'attributes.stickinessEnabled', event.target.checked)}
                            />
                              {' '}<label>Sticky</label><HelpField id="aws.targetGroup.attributes.stickinessEnabled"/>
                          </label>
                        </span>
                        {targetGroup.attributes.stickinessEnabled && (
                          <span className="wizard-pod-content">
                            <label>Duration </label><HelpField id="aws.targetGroup.attributes.stickinessDuration"/>
                            {' '}
                            <input
                              className="form-control input-sm inline-number"
                              value={targetGroup.attributes.stickinessDuration}
                              onChange={(event) => this.targetGroupFieldChanged(index, 'attributes.stickinessDuration', event.target.value)}
                              type="number"
                            />
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ))}
            <table className="table table-condensed packed">
              <tbody>
                <tr>
                  <td>
                    <button type="button" className="add-new col-md-12" onClick={this.addTargetGroup}>
                      <span className="glyphicon glyphicon-plus-sign"/> Add new target group
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  }
};

export const TargetGroups = wizardPage<ITargetGroupsProps>(TargetGroupsImpl);
