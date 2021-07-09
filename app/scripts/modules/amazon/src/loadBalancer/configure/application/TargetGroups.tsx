import { FormikErrors, FormikProps } from 'formik';
import { filter, flatten, groupBy, set, uniq } from 'lodash';
import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  Application,
  FormValidator,
  HelpField,
  IWizardPageComponent,
  spelNumberCheck,
  SpelNumberInput,
  SpInput,
  ValidationMessage,
  Validators,
} from '@spinnaker/core';

import { isNameInUse, isNameLong, isValidHealthCheckInterval, isValidTimeout } from '../common/targetGroupValidators';
import { IAmazonApplicationLoadBalancer, IAmazonApplicationLoadBalancerUpsertCommand } from '../../../domain';

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

export class TargetGroups
  extends React.Component<ITargetGroupsProps, ITargetGroupsState>
  implements IWizardPageComponent<IAmazonApplicationLoadBalancerUpsertCommand> {
  public protocols = ['HTTP', 'HTTPS'];
  public targetTypes = ['instance', 'ip', 'lambda'];
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
    values: IAmazonApplicationLoadBalancerUpsertCommand,
  ): FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand> {
    const duplicateTargetGroups = uniq(
      flatten(filter(groupBy(values.targetGroups, 'name'), (count) => count.length > 1)).map((tg) => tg.name),
    );

    const formValidator = new FormValidator(values);
    const { arrayForEach } = formValidator;

    formValidator.field('targetGroups').withValidators(
      arrayForEach((builder, item) => {
        builder
          .field('name', 'Name')
          .withValidators(
            isNameInUse(this.state.existingTargetGroupNames, values.credentials, values.region),
            isNameLong(this.props.app.name.length),
            Validators.valueUnique(
              duplicateTargetGroups,
              'There is already a target group in this load balancer with the same name.',
            ),
          );
        builder
          .field('healthCheckInterval', 'Health Check Interval')
          .withValidators(isValidHealthCheckInterval(item), Validators.checkBetween('healthCheckInterval', 5, 300));
        builder
          .field('healthyThreshold', 'Healthy Threshold')
          .withValidators(Validators.checkBetween('healthyThreshold', 2, 10));
        builder
          .field('unhealthyThreshold', 'Unhealthy Threshold')
          .spelAware()
          .withValidators(Validators.checkBetween('unhealthyThreshold', 2, 10));
        builder
          .field('healthCheckTimeout', 'Timeout')
          .withValidators(isValidTimeout(item), Validators.checkBetween('healthCheckTimeout', 2, 120));

        if (item.targetType !== 'lambda') {
          builder.field('protocol', 'Protocol').required();
          builder.field('healthCheckPath', 'Health Check Path').required();
          builder.field('healthCheckProtocol', 'Health Check Protocol').required();
          builder.field('name', 'Name').required();
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
            .field('healthCheckInterval', 'Health Check Interval')
            .required()
            .spelAware()
            .withValidators((value) => spelNumberCheck(value));
          builder
            .field('healthCheckPort', 'Health Check Port')
            .required()
            .spelAware()
            .withValidators((value) => (value === 'traffic-port' ? null : spelNumberCheck(value)));
          builder
            .field('port', 'Port')
            .required()
            .spelAware()
            .withValidators((value) => spelNumberCheck(value));
          builder
            .field('healthyThreshold', 'Healthy Threshold')
            .required()
            .spelAware()
            .withValidators((value) => spelNumberCheck(value), Validators.checkBetween('healthyThreshold', 2, 10));
          builder
            .field('unhealthyThreshold', 'Unhealthy Threshold')
            .required()
            .spelAware()
            .withValidators((value) => spelNumberCheck(value), Validators.checkBetween('unhealthyThreshold', 2, 10));
        }
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

  private targetGroupFieldChanged(index: number, field: string, value: string | boolean | number): void {
    const { setFieldValue, values } = this.props.formik;
    const targetGroup = values.targetGroups[index];
    if (field === 'targetType' && value === 'lambda') {
      delete targetGroup.port;
    }
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
        deregistrationDelay: 300,
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

    const ProtocolOptions = this.protocols.map((p) => <option key={p}>{p}</option>);
    const TargetTypeOptions = this.targetTypes.map((p) => <option key={p}>{p}</option>);
    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-12">
            {values.targetGroups.map((targetGroup, index) => {
              const tgErrors = (errors.targetGroups && errors.targetGroups[index]) || {};
              const has6sTimeout =
                (targetGroup.protocol === 'TCP' || targetGroup.protocol === 'TLS') &&
                targetGroup.healthCheckProtocol === 'HTTP';
              const has10sTimeout =
                (targetGroup.protocol === 'TCP' || targetGroup.protocol === 'TLS') &&
                targetGroup.healthCheckProtocol === 'HTTPS';

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
                    {targetGroup.targetType !== 'lambda' && (
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
                                onChange={(event) =>
                                  this.targetGroupFieldChanged(index, 'protocol', event.target.value)
                                }
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
                    )}
                    <div className="wizard-pod-row">
                      <div className="wizard-pod-row-title">Healthcheck</div>
                      <div className="wizard-pod-row-contents">
                        <div className="wizard-pod-row-data">
                          {targetGroup.targetType !== 'lambda' && (
                            <span className="wizard-pod-content">
                              <label>Protocol </label>
                              {targetGroup.healthCheckProtocol === 'TCP' && (
                                <HelpField id="aws.targetGroup.healthCheckProtocol" />
                              )}{' '}
                              <select
                                className="form-control input-sm inline-number"
                                value={targetGroup.healthCheckProtocol}
                                onChange={(event) =>
                                  this.targetGroupFieldChanged(index, 'healthCheckProtocol', event.target.value)
                                }
                              >
                                {ProtocolOptions}
                              </select>
                            </span>
                          )}
                          {targetGroup.targetType !== 'lambda' && (
                            <span className="wizard-pod-content">
                              <label>Port </label>
                              <HelpField id="aws.targetGroup.attributes.healthCheckPort.trafficPort" />{' '}
                              <select
                                className="form-control input-sm inline-number"
                                style={{ width: '90px' }}
                                value={targetGroup.healthCheckPort === 'traffic-port' ? 'traffic-port' : 'manual'}
                                onChange={(event) =>
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
                                onChange={(event) =>
                                  this.targetGroupFieldChanged(index, 'healthCheckPort', event.target.value)
                                }
                              />
                            </span>
                          )}
                          <span className="wizard-pod-content">
                            <label>Path </label>
                            <SpInput
                              error={tgErrors.healthCheckPath}
                              name="healthCheckPath"
                              required={true}
                              value={targetGroup.healthCheckPath}
                              onChange={(event) =>
                                this.targetGroupFieldChanged(index, 'healthCheckPath', event.target.value)
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <label>Timeout </label>
                            {(has6sTimeout || has10sTimeout) && <HelpField id="aws.targetGroup.healthCheckTimeout" />}
                            <SpelNumberInput
                              error={tgErrors.healthCheckTimeout}
                              disabled={has6sTimeout || has10sTimeout}
                              required={true}
                              value={has6sTimeout ? 6 : has10sTimeout ? 10 : targetGroup.healthCheckTimeout}
                              min={2}
                              max={120}
                              onChange={(value: string | number) =>
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
                              min={5}
                              max={300}
                              onChange={(value: string | number) =>
                                this.targetGroupFieldChanged(index, 'healthCheckInterval', value)
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
                            <SpelNumberInput
                              error={tgErrors.healthyThreshold}
                              value={targetGroup.healthyThreshold}
                              min={2}
                              max={10}
                              onChange={(value: string | number) =>
                                this.targetGroupFieldChanged(index, 'healthyThreshold', value)
                              }
                            />
                          </span>
                          <span className="wizard-pod-content">
                            <label>Unhealthy </label>
                            <SpelNumberInput
                              error={tgErrors.unhealthyThreshold}
                              required={true}
                              value={targetGroup.unhealthyThreshold}
                              min={2}
                              max={10}
                              onChange={(value: string | number) =>
                                this.targetGroupFieldChanged(index, 'unhealthyThreshold', value)
                              }
                            />
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="wizard-pod-row">
                      <div className="wizard-pod-row-title">Attributes</div>
                      {targetGroup.targetType !== 'lambda' ? (
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
                              <label className="checkbox-inline" style={{ paddingTop: '2px' }}>
                                <input
                                  type="checkbox"
                                  checked={targetGroup.attributes.stickinessEnabled}
                                  onChange={(event) =>
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
                                  onChange={(event) =>
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
                      ) : (
                        <span className="wizard-pod-content">
                          <label className="checkbox-inline" style={{ paddingTop: '2px' }}>
                            <input
                              type="checkbox"
                              checked={targetGroup.attributes.multiValueHeadersEnabled}
                              onChange={(event) =>
                                this.targetGroupFieldChanged(
                                  index,
                                  'attributes.multiValueHeadersEnabled',
                                  event.target.checked,
                                )
                              }
                            />{' '}
                            <label>Enable Multi Value Headers</label>
                          </label>
                        </span>
                      )}
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
