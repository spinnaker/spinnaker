import * as React from 'react';
import { filter, flatten, get, groupBy, set, uniq } from 'lodash';
import { FormikErrors, FormikProps } from 'formik';
import { Observable, Subject } from 'rxjs';

import {
  Application,
  HelpField,
  IWizardPageComponent,
  SpInput,
  ValidationMessage,
  spelNumberCheck,
  Validators,
  robotToHuman,
} from '@spinnaker/core';

import { IAmazonApplicationLoadBalancer, IAmazonApplicationLoadBalancerUpsertCommand } from 'amazon/domain';

export interface ITargetGroupsProps {
  app: Application;
  formik: FormikProps<IAmazonApplicationLoadBalancerUpsertCommand>;
  isNew: boolean;
  loadBalancer: IAmazonApplicationLoadBalancer;
}

export interface ITargetGroupsState {
  existingTargetGroupNames: { [account: string]: { [region: string]: string[] } };
  oldTargetGroupCount: number;
}

export class TargetGroups extends React.Component<ITargetGroupsProps, ITargetGroupsState>
  implements IWizardPageComponent<IAmazonApplicationLoadBalancerUpsertCommand> {
  public protocols = ['HTTP', 'HTTPS'];
  public targetTypes = ['instance', 'ip'];
  private destroy$ = new Subject();

  constructor(props: ITargetGroupsProps) {
    super(props);

    const oldTargetGroupCount = !props.isNew ? props.formik.initialValues.targetGroups.length : 0;
    this.state = {
      existingTargetGroupNames: {},
      oldTargetGroupCount,
    };
  }

  private checkBetween(errors: any, object: any, fieldName: string, min: number, max: number) {
    const field = object[fieldName];
    if (!Number.isNaN(field)) {
      errors[fieldName] =
        Validators.minValue(min)(field, robotToHuman(fieldName)) ||
        Validators.maxValue(max)(field, robotToHuman(fieldName));
    }
  }

  public validate(
    values: IAmazonApplicationLoadBalancerUpsertCommand,
  ): FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand> {
    const errors = {} as any;

    let hasErrors = false;
    const duplicateTargetGroups = uniq(
      flatten(filter(groupBy(values.targetGroups, 'name'), count => count.length > 1)).map(tg => tg.name),
    );
    const targetGroupsErrors = values.targetGroups.map((targetGroup: any) => {
      const tgErrors: { [key: string]: string } = {};

      if (
        targetGroup.name &&
        get(this.state.existingTargetGroupNames, [values.credentials, values.region], []).includes(
          targetGroup.name.toLowerCase(),
        )
      ) {
        tgErrors.name = `There is already a target group in ${values.credentials}:${values.region} with that name.`;
      }

      if (targetGroup.name && targetGroup.name.length > 32 - this.props.app.name.length) {
        tgErrors.name =
          'Target group name is automatically prefixed with the application name and cannot exceed 32 characters in length.';
      }

      if (duplicateTargetGroups.includes(targetGroup.name)) {
        tgErrors.name = 'Duplicate target group name in this load balancer.';
      }

      ['port', 'healthCheckInterval', 'healthyThreshold', 'unhealthyThreshold'].forEach(key => {
        const err = spelNumberCheck(targetGroup[key]);
        if (err) {
          tgErrors[key] = err;
        }
      });

      this.checkBetween(tgErrors, targetGroup, 'healthCheckTimeout', 2, 60);
      this.checkBetween(tgErrors, targetGroup, 'healthCheckInterval', 5, 300);
      this.checkBetween(tgErrors, targetGroup, 'healthyThreshold', 2, 10);
      this.checkBetween(tgErrors, targetGroup, 'unhealthyThreshold', 2, 10);

      if (targetGroup.healthCheckPort !== 'traffic-port') {
        const err = spelNumberCheck(targetGroup.healthCheckPort);
        if (err) {
          tgErrors.healthCheckPort = err;
        }
      }

      [
        'name',
        'protocol',
        'port',
        'healthCheckInterval',
        'healthCheckPath',
        'healthCheckPort',
        'healthCheckProtocol',
        'healthyThreshold',
        'unhealthyThreshold',
      ].forEach(key => {
        if (!targetGroup[key]) {
          tgErrors[key] = 'Required';
        }
      });

      if (Object.keys(tgErrors).length > 0) {
        hasErrors = true;
      }
      return tgErrors;
    });

    if (hasErrors) {
      errors.targetGroups = targetGroupsErrors;
    }
    return errors;
  }

  private removeAppName(name: string): string {
    return name.replace(`${this.props.app.name}-`, '');
  }

  protected updateLoadBalancerNames(props: ITargetGroupsProps): void {
    const { app, loadBalancer } = props;

    const targetGroupsByAccountAndRegion: { [account: string]: { [region: string]: string[] } } = {};
    Observable.fromPromise(app.getDataSource('loadBalancers').refresh(true))
      .takeUntil(this.destroy$)
      .subscribe(() => {
        app.getDataSource('loadBalancers').data.forEach((lb: IAmazonApplicationLoadBalancer) => {
          if (lb.loadBalancerType !== 'classic') {
            if (!loadBalancer || lb.name !== loadBalancer.name) {
              lb.targetGroups.forEach(targetGroup => {
                targetGroupsByAccountAndRegion[lb.account] = targetGroupsByAccountAndRegion[lb.account] || {};
                targetGroupsByAccountAndRegion[lb.account][lb.region] =
                  targetGroupsByAccountAndRegion[lb.account][lb.region] || [];
                targetGroupsByAccountAndRegion[lb.account][lb.region].push(this.removeAppName(targetGroup.name));
              });
            }
          }
        });

        this.setState({ existingTargetGroupNames: targetGroupsByAccountAndRegion }, () =>
          this.props.formik.validateForm(),
        );
      });
  }

  private targetGroupFieldChanged(index: number, field: string, value: string | boolean): void {
    const { setFieldValue, values } = this.props.formik;
    const targetGroup = values.targetGroups[index];
    set(targetGroup, field, value);
    setFieldValue('targetGroups', values.targetGroups);
  }

  private addTargetGroup = (): void => {
    const { setFieldValue, values } = this.props.formik;
    const tgLength = values.targetGroups.length;
    values.targetGroups.push({
      name: `targetgroup${tgLength ? `${tgLength}` : ''}`,
      protocol: 'HTTP',
      port: 7001,
      targetType: 'instance',
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
        stickinessDuration: 8400,
      },
    });
    setFieldValue('targetGroups', values.targetGroups);
  };

  private removeTargetGroup(index: number): void {
    const { setFieldValue, values } = this.props.formik;
    const { oldTargetGroupCount } = this.state;
    values.targetGroups.splice(index, 1);

    if (index < oldTargetGroupCount) {
      this.setState({ oldTargetGroupCount: oldTargetGroupCount - 1 });
    }
    setFieldValue('targetGroups', values.targetGroups);
  }

  public componentDidMount(): void {
    this.updateLoadBalancerNames(this.props);
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  public render() {
    const { app } = this.props;
    const { errors, values } = this.props.formik;
    const { oldTargetGroupCount } = this.state;

    const ProtocolOptions = this.protocols.map(p => <option key={p}>{p}</option>);
    const TargetTypeOptions = this.targetTypes.map(p => <option key={p}>{p}</option>);

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-12">
            {values.targetGroups.map((targetGroup, index) => {
              const tgErrors = (errors.targetGroups && errors.targetGroups[index]) || {};
              return (
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
                            onChange={event => this.targetGroupFieldChanged(index, 'name', event.target.value)}
                            required={true}
                            disabled={index < oldTargetGroupCount}
                          />
                          <a className="sm-label clickable" onClick={() => this.removeTargetGroup(index)}>
                            <span className="glyphicon glyphicon-trash" />
                          </a>
                        </div>
                        {tgErrors.name && (
                          <div className="wizard-pod-row-errors">
                            <ValidationMessage type="error" message={tgErrors.name} />
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="wizard-pod-row">
                      <div className="wizard-pod-row-title">
                        <HelpField id="aws.targetGroup.targetType" /> <span>Target Type&nbsp;</span>
                      </div>
                      <div className="wizard-pod-row-contents">
                        <div className="wizard-pod-row-data">
                          <span className="wizard-pod-content">
                            <select
                              className="form-control input-sm"
                              value={targetGroup.targetType}
                              onChange={event => this.targetGroupFieldChanged(index, 'targetType', event.target.value)}
                              disabled={index < oldTargetGroupCount}
                            >
                              {TargetTypeOptions}
                            </select>
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="wizard-pod-row">
                      <div className="wizard-pod-row-title">Backend Connection</div>
                      <div className="wizard-pod-row-contents">
                        <div className="wizard-pod-row-data">
                          <span className="wizard-pod-content">
                            <label>Protocol </label>
                            <HelpField id="aws.targetGroup.protocol" />{' '}
                            <select
                              className="form-control input-sm inline-number"
                              value={targetGroup.protocol}
                              onChange={event => this.targetGroupFieldChanged(index, 'protocol', event.target.value)}
                              disabled={index < oldTargetGroupCount}
                            >
                              {ProtocolOptions}
                            </select>
                          </span>
                          <span className="wizard-pod-content">
                            <label>Port </label>
                            <HelpField id="aws.targetGroup.port" />{' '}
                            <input
                              className="form-control input-sm inline-number"
                              value={targetGroup.port}
                              onChange={event => this.targetGroupFieldChanged(index, 'port', event.target.value)}
                              type="text"
                              required={true}
                              disabled={index < oldTargetGroupCount}
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
                              onChange={event =>
                                this.targetGroupFieldChanged(index, 'healthCheckProtocol', event.target.value)
                              }
                            >
                              {ProtocolOptions}
                            </select>
                          </span>
                          <span className="wizard-pod-content">
                            <label>Port </label>
                            <HelpField id="aws.targetGroup.attributes.healthCheckPort.trafficPort" />{' '}
                            <select
                              className="form-control input-sm inline-number"
                              style={{ width: '90px' }}
                              value={targetGroup.healthCheckPort === 'traffic-port' ? 'traffic-port' : 'manual'}
                              onChange={event =>
                                this.targetGroupFieldChanged(
                                  index,
                                  'healthCheckPort',
                                  event.target.value === 'traffic-port' ? 'traffic-port' : '',
                                )
                              }
                            >
                              <option value="traffic-port">Traffic Port</option>
                              <option value="manual">Manual</option>
                            </select>{' '}
                            <SpInput
                              className="form-control input-sm inline-number"
                              error={tgErrors.healthCheckPort}
                              style={{
                                visibility: targetGroup.healthCheckPort === 'traffic-port' ? 'hidden' : 'inherit',
                              }}
                              name="healthCheckPort"
                              required={true}
                              value={targetGroup.healthCheckPort}
                              onChange={event =>
                                this.targetGroupFieldChanged(index, 'healthCheckPort', event.target.value)
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <label>Path </label>
                            <SpInput
                              className="form-control input-sm inline-text"
                              error={tgErrors.healthCheckPath}
                              name="healthCheckPath"
                              required={true}
                              value={targetGroup.healthCheckPath}
                              onChange={event =>
                                this.targetGroupFieldChanged(index, 'healthCheckPath', event.target.value)
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <label>Timeout </label>
                            <SpInput
                              className="form-control input-sm inline-number"
                              error={tgErrors.healthCheckTimeout}
                              name="healthCheckTimeout"
                              required={true}
                              value={targetGroup.healthCheckTimeout}
                              onChange={event =>
                                this.targetGroupFieldChanged(index, 'healthCheckTimeout', event.target.value)
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <label>Interval </label>
                            <SpInput
                              className="form-control input-sm inline-number"
                              error={tgErrors.healthCheckInterval}
                              name="healthCheckInterval"
                              required={true}
                              value={targetGroup.healthCheckInterval}
                              onChange={event =>
                                this.targetGroupFieldChanged(index, 'healthCheckInterval', event.target.value)
                              }
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
                            <SpInput
                              className="form-control input-sm inline-number"
                              error={tgErrors.healthyThreshold}
                              name="healthyThreshold"
                              type="text"
                              value={targetGroup.healthyThreshold}
                              onChange={event =>
                                this.targetGroupFieldChanged(index, 'healthyThreshold', event.target.value)
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <label>Unhealthy </label>
                            <SpInput
                              className="form-control input-sm inline-number"
                              error={tgErrors.unhealthyThreshold}
                              name="unhealthyThreshold"
                              required={true}
                              value={targetGroup.unhealthyThreshold}
                              onChange={event =>
                                this.targetGroupFieldChanged(index, 'unhealthyThreshold', event.target.value)
                              }
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
                            <label>Dereg. Delay</label>
                            <HelpField id="aws.targetGroup.attributes.deregistrationDelay" />{' '}
                            <input
                              className="form-control input-sm inline-number"
                              type="text"
                              value={targetGroup.attributes.deregistrationDelay}
                              onChange={event =>
                                this.targetGroupFieldChanged(
                                  index,
                                  'attributes.deregistrationDelay',
                                  event.target.value,
                                )
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <label className="checkbox-inline" style={{ paddingTop: '2px' }}>
                              <input
                                type="checkbox"
                                checked={targetGroup.attributes.stickinessEnabled}
                                onChange={event =>
                                  this.targetGroupFieldChanged(
                                    index,
                                    'attributes.stickinessEnabled',
                                    event.target.checked,
                                  )
                                }
                              />{' '}
                              <label>Sticky</label>
                              <HelpField id="aws.targetGroup.attributes.stickinessEnabled" />
                            </label>
                          </span>
                          {targetGroup.attributes.stickinessEnabled && (
                            <span className="wizard-pod-content">
                              <label>Duration </label>
                              <HelpField id="aws.targetGroup.attributes.stickinessDuration" />{' '}
                              <input
                                className="form-control input-sm inline-number"
                                value={targetGroup.attributes.stickinessDuration}
                                onChange={event =>
                                  this.targetGroupFieldChanged(
                                    index,
                                    'attributes.stickinessDuration',
                                    event.target.value,
                                  )
                                }
                                type="text"
                              />
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
            <table className="table table-condensed packed">
              <tbody>
                <tr>
                  <td>
                    <button type="button" className="add-new col-md-12" onClick={this.addTargetGroup}>
                      <span className="glyphicon glyphicon-plus-sign" /> Add new target group
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
}
