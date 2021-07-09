import { FormikErrors, FormikProps } from 'formik';
import { filter, flatten, groupBy, set, uniq } from 'lodash';
import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  Application,
  CheckboxInput,
  FormValidator,
  HelpField,
  IWizardPageComponent,
  spelNumberCheck,
  SpelNumberInput,
  SpInput,
  ValidationMessage,
  Validators,
} from '@spinnaker/core';

import { isNameInUse, isNameLong } from '../common/targetGroupValidators';
import { IAmazonApplicationLoadBalancer, IAmazonNetworkLoadBalancerUpsertCommand } from '../../../domain';

export interface ITargetGroupsProps {
  app: Application;
  formik: FormikProps<IAmazonNetworkLoadBalancerUpsertCommand>;
  isNew: boolean;
  loadBalancer: IAmazonApplicationLoadBalancer;
}

export interface ITargetGroupsState {
  existingTargetGroupNames: { [account: string]: { [region: string]: string[] } };
  oldTargetGroupCount: number;
}

export class TargetGroups
  extends React.Component<ITargetGroupsProps, ITargetGroupsState>
  implements IWizardPageComponent<IAmazonNetworkLoadBalancerUpsertCommand> {
  public protocols = ['TCP', 'UDP'];
  public healthProtocols = ['TCP', 'HTTP', 'HTTPS'];
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

  public validate(
    values: IAmazonNetworkLoadBalancerUpsertCommand,
  ): FormikErrors<IAmazonNetworkLoadBalancerUpsertCommand> {
    const duplicateTargetGroups = uniq(
      flatten(filter(groupBy(values.targetGroups, 'name'), (count) => count.length > 1)).map((tg) => tg.name),
    );
    const formValidator = new FormValidator(values);
    const { arrayForEach } = formValidator;

    formValidator.field('targetGroups').withValidators(
      arrayForEach((builder) => {
        builder
          .field('name', 'Name')
          .required()
          .withValidators(
            isNameInUse(this.state.existingTargetGroupNames, values.credentials, values.region),
            isNameLong(this.props.app.name.length),
            Validators.valueUnique(
              duplicateTargetGroups,
              'There is already a target group in this load balancer with the same name.',
            ),
          );
        builder
          .field('port', 'Port')
          .required()
          .spelAware()
          .withValidators((value) => spelNumberCheck(value));
        builder
          .field('healthCheckInterval', 'Health Check Interval')
          .required()
          .spelAware()
          .withValidators((value) => spelNumberCheck(value));
        builder
          .field('healthyThreshold', 'Healthy Threshold')
          .required()
          .spelAware()
          .withValidators((value) => spelNumberCheck(value));
        builder
          .field('unhealthyThreshold', 'Unhealthy Threshold')
          .required()
          .spelAware()
          .withValidators((value) => spelNumberCheck(value));
        builder
          .field('healthCheckPort', 'Health Check Port')
          .required()
          .spelAware()
          .withValidators((value) => (value === 'traffic-port' ? null : spelNumberCheck(value)));
        builder.field('protocol', 'Protocol').required();
        builder.field('healthCheckProtocol', 'Health Check Protocol').required();
      }),
    );
    return formValidator.validateForm();
  }

  private removeAppName(name: string): string {
    return name.replace(`${this.props.app.name}-`, '');
  }

  protected updateLoadBalancerNames(props: ITargetGroupsProps): void {
    const { app, loadBalancer } = props;

    const targetGroupsByAccountAndRegion: { [account: string]: { [region: string]: string[] } } = {};
    observableFrom(app.getDataSource('loadBalancers').refresh(true))
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        app.getDataSource('loadBalancers').data.forEach((lb: IAmazonApplicationLoadBalancer) => {
          if (lb.loadBalancerType !== 'classic') {
            if (!loadBalancer || lb.name !== loadBalancer.name) {
              lb.targetGroups.forEach((targetGroup) => {
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
    if (field === 'healthyThreshold') {
      set(targetGroup, 'unhealthyThreshold', value);
    }
    setFieldValue('targetGroups', values.targetGroups);
  }

  private addTargetGroup = (): void => {
    const { setFieldValue, values } = this.props.formik;
    const tgLength = values.targetGroups.length;
    values.targetGroups.push({
      name: `targetgroup${tgLength ? `${tgLength}` : ''}`,
      protocol: 'TCP',
      port: 7001,
      targetType: 'instance',
      healthCheckProtocol: 'TCP',
      healthCheckPort: 'traffic-port',
      healthCheckPath: '/healthcheck',
      healthCheckTimeout: 5,
      healthCheckInterval: 10,
      healthyThreshold: 10,
      unhealthyThreshold: 10,
      attributes: {
        deregistrationDelay: 300,
        preserveClientIp: true,
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

    const ProtocolOptions = this.protocols.map((p) => <option key={p}>{p}</option>);
    const HealthProtocolOptions = this.healthProtocols.map((p) => <option key={p}>{p}</option>);
    const TargetTypeOptions = this.targetTypes.map((p) => <option key={p}>{p}</option>);

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
                            onChange={(event) => this.targetGroupFieldChanged(index, 'name', event.target.value)}
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
                              onChange={(event) =>
                                this.targetGroupFieldChanged(index, 'targetType', event.target.value)
                              }
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
                              onChange={(event) => this.targetGroupFieldChanged(index, 'protocol', event.target.value)}
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
                              onChange={(event) => this.targetGroupFieldChanged(index, 'port', event.target.value)}
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
                              disabled={index < oldTargetGroupCount}
                              className="form-control input-sm inline-number"
                              value={targetGroup.healthCheckProtocol}
                              onChange={(event) =>
                                this.targetGroupFieldChanged(index, 'healthCheckProtocol', event.target.value)
                              }
                            >
                              {HealthProtocolOptions}
                            </select>
                          </span>
                          <span className="wizard-pod-content">
                            <label>Port </label>
                            <SpInput
                              className="form-control input-sm inline-number"
                              error={tgErrors.healthCheckPort}
                              name="healthCheckPort"
                              required={true}
                              value={targetGroup.healthCheckPort}
                              onChange={(event) =>
                                this.targetGroupFieldChanged(index, 'healthCheckPort', event.target.value)
                              }
                            />
                          </span>
                          {targetGroup.healthCheckProtocol !== 'TCP' && (
                            <span className="wizard-pod-content">
                              <label>Path </label>
                              <SpInput
                                className="form-control input-sm inline-text"
                                error={tgErrors.healthCheckPath}
                                name="healthCheckPath"
                                required={true}
                                value={targetGroup.healthCheckPath}
                                onChange={(event) =>
                                  this.targetGroupFieldChanged(index, 'healthCheckPath', event.target.value)
                                }
                              />
                            </span>
                          )}
                          <span className="wizard-pod-content">
                            <label>Timeout </label>
                            <SpelNumberInput
                              error={tgErrors.healthCheckTimeout}
                              required={true}
                              value={targetGroup.healthCheckTimeout}
                              onChange={(value: string) =>
                                this.targetGroupFieldChanged(index, 'healthCheckTimeout', value)
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <label>Interval </label>
                            <SpelNumberInput
                              error={tgErrors.healthCheckInterval}
                              required={true}
                              value={targetGroup.healthCheckInterval}
                              onChange={(value: string) =>
                                this.targetGroupFieldChanged(index, 'healthCheckInterval', value)
                              }
                            />
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="wizard-pod-row">
                      <div className="wizard-pod-row-title">
                        <HelpField id="aws.targetGroup.nlbHealthcheckThreshold" />{' '}
                        <span>Healthcheck Threshold&nbsp;</span>
                      </div>
                      <div className="wizard-pod-row-contents">
                        <div className="wizard-pod-row-data">
                          <span className="wizard-pod-content">
                            <SpelNumberInput
                              error={tgErrors.healthyThreshold}
                              value={targetGroup.healthyThreshold}
                              onChange={(value: string) =>
                                this.targetGroupFieldChanged(index, 'healthyThreshold', value)
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
                              onChange={(event) =>
                                this.targetGroupFieldChanged(
                                  index,
                                  'attributes.deregistrationDelay',
                                  event.target.value,
                                )
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <CheckboxInput
                              name="deregistrationDelayConnectionTermination"
                              text="Connection Termination"
                              checked={targetGroup.attributes.deregistrationDelayConnectionTermination}
                              onChange={(event: { target: { checked: boolean } }) => {
                                this.targetGroupFieldChanged(
                                  index,
                                  'attributes.deregistrationDelayConnectionTermination',
                                  event.target.checked,
                                );
                              }}
                            />
                            <HelpField id="aws.targetGroup.attributes.deregistrationDelayConnectionTermination" />
                          </span>
                          <span className="wizard-pod-content">
                            <CheckboxInput
                              name="preserveClientIp"
                              text="Preserve Client IP"
                              checked={targetGroup.attributes.preserveClientIp}
                              onChange={(event: { target: { checked: boolean } }) => {
                                this.targetGroupFieldChanged(
                                  index,
                                  'attributes.preserveClientIp',
                                  event.target.checked,
                                );
                              }}
                            />
                            <HelpField id="aws.targetGroup.attributes.preserveClientIp" />
                          </span>
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
