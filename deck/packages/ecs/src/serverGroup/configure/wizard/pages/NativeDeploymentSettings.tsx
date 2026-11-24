import React from 'react';

import { HelpField } from '@spinnaker/core';

import type { IEcsWizardPageProps } from './common';
import type { IEcsServerGroupCommand } from '../../serverGroupConfiguration.service';
import { isEcsNativeBlueGreenStrategy } from '../../serverGroupConfiguration.service';

const ECS_NATIVE = 'ecs-native';

export const isEcsNative = (command: IEcsServerGroupCommand): boolean => command.cloudProvider === ECS_NATIVE;

export const NativeDeploymentSettings = ({ command, onFieldChange }: IEcsWizardPageProps) => {
  const useNative = isEcsNative(command);
  const alarms = command.backingData?.filtered?.metricAlarms || [];
  const alarmNames: string[] = command.alarmNames || [];
  const strategy = command.deploymentStrategy || 'ROLLING';
  const isBlueGreen = isEcsNativeBlueGreenStrategy(strategy);
  // When a load balancer is attached, AWS ECS requires the ALB traffic-shift config for BLUE_GREEN;
  // it is only truly optional for a service with no load balancer (ECS rejects a load-balanced
  // BLUE_GREEN deploy without advancedConfiguration; see validateEcsNativeDeployment).
  const hasLoadBalancer =
    !!command.targetGroup || (command.targetGroupMappings || []).some((mapping) => !!mapping?.targetGroup);
  const albShiftRequired = isBlueGreen && hasLoadBalancer;

  const toggleAlarm = (alarmName: string, checked: boolean) => {
    const next = checked ? [...alarmNames, alarmName] : alarmNames.filter((name) => name !== alarmName);
    onFieldChange('alarmNames', next);
  };

  return (
    <div className="container-fluid form-horizontal" data-test-id="EcsServerGroupWizard.nativeDeployment">
      <div className="form-group">
        <div className="col-md-5 sm-label-right">
          Use native ECS deployment <HelpField id="ecs.native.useEcsNative" />
        </div>
        <div className="col-md-3">
          <input
            aria-label="Use native ECS deployment"
            checked={useNative}
            data-test-id="NativeDeployment.useEcsNative"
            // The wizard's command update (updateEcsCommand) reacts to this one field change: it
            // forces the Spinnaker strategy to None while native is on, and on the way back restores
            // the previous strategy and drops the native-only fields.
            onChange={(event) => onFieldChange('cloudProvider', event.target.checked ? ECS_NATIVE : 'ecs')}
            type="checkbox"
          />
        </div>
      </div>

      {!useNative && (
        <div style={{ color: '#666' }}>
          <p>
            <em>
              When enabled, this deploy updates a durable ECS service in place through ECS&apos;s own Rolling or
              Blue/Green deployment (deployment alarms, circuit breaker, and bake time) instead of Spinnaker&apos;s
              red/black orchestration. Blue/Green can pause on an ECS lifecycle hook for downstream Continue/Stop ECS
              Native Service Deployment stages. The account, cluster, task definition, and networking configured
              elsewhere in this wizard are unchanged.
            </em>
          </p>
        </div>
      )}

      {useNative && (
        <>
          <div className="form-group">
            <div className="col-md-5 sm-label-right">
              Circuit breaker rollback <HelpField id="ecs.native.deploymentCircuitBreakerRollback" />
            </div>
            <div className="col-md-3">
              <input
                aria-label="Automatically roll back a failed deployment"
                checked={!!command.deploymentCircuitBreakerRollback}
                data-test-id="NativeDeployment.deploymentCircuitBreakerRollback"
                disabled={!command.enableDeploymentCircuitBreaker}
                onChange={(event) => onFieldChange('deploymentCircuitBreakerRollback', event.target.checked)}
                type="checkbox"
              />
            </div>
            <div className="col-md-12">
              <span className="help-block" style={{ marginLeft: '0' }}>
                Requires <b>Enable Deployment Circuit Breaker</b> (Advanced Settings). When both are set, ECS
                automatically rolls back a failed deployment to the last completed one.
              </span>
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-5 sm-label-right">
              <b>Deployment Alarms</b> <HelpField id="ecs.native.alarmNames" />
            </div>
            <div className="col-md-7">
              {alarms.length ? (
                alarms.map((alarm: any) => (
                  <div className="checkbox" key={alarm.alarmName}>
                    <label>
                      <input
                        checked={alarmNames.includes(alarm.alarmName)}
                        onChange={(event) => toggleAlarm(alarm.alarmName, event.target.checked)}
                        type="checkbox"
                      />{' '}
                      {alarm.alarmName}
                    </label>
                  </div>
                ))
              ) : (
                <span className="help-block">No account was selected, or no CloudWatch alarms are available.</span>
              )}
            </div>
          </div>

          {alarmNames.length > 0 && (
            <div className="form-group">
              <div className="col-md-5 sm-label-right">
                Roll back on alarm <HelpField id="ecs.native.deploymentAlarmsRollback" />
              </div>
              <div className="col-md-3">
                <input
                  aria-label="Automatically roll back a deployment that trips a named alarm"
                  checked={!!command.deploymentAlarmsRollback}
                  data-test-id="NativeDeployment.deploymentAlarmsRollback"
                  onChange={(event) => onFieldChange('deploymentAlarmsRollback', event.target.checked)}
                  type="checkbox"
                />
              </div>
            </div>
          )}

          <div className="form-group">
            <div className="col-md-5 sm-label-right">
              <b>Deployment Strategy</b> <HelpField id="ecs.native.deploymentStrategy" />
            </div>
            <div className="col-md-7">
              <select
                aria-label="Native ECS deployment strategy"
                className="form-control input-sm"
                data-test-id="NativeDeployment.deploymentStrategy"
                onChange={(event) => onFieldChange('deploymentStrategy', event.target.value)}
                value={strategy}
              >
                <option value="ROLLING">Rolling</option>
                <option value="BLUE_GREEN">Blue/Green</option>
              </select>
              <span className="help-block">
                Blue/Green lifecycle control uses downstream Continue and Stop ECS Native Service Deployment stages that
                reference this deploy stage; Orca resolves the exact ECS service-deployment ARN from it.
              </span>
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-5 sm-label-right">Minimum healthy percent</div>
            <div className="col-md-2">
              <input
                aria-label="Minimum healthy percent"
                className="form-control input-sm no-spel"
                data-test-id="NativeDeployment.minimumHealthyPercent"
                onChange={(event) =>
                  onFieldChange('minimumHealthyPercent', event.target.value === '' ? null : Number(event.target.value))
                }
                type="number"
                min="0"
                max="100"
                value={command.minimumHealthyPercent ?? ''}
              />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-5 sm-label-right">Maximum percent</div>
            <div className="col-md-2">
              <input
                aria-label="Maximum percent"
                className="form-control input-sm no-spel"
                data-test-id="NativeDeployment.maximumPercent"
                onChange={(event) =>
                  onFieldChange('maximumPercent', event.target.value === '' ? null : Number(event.target.value))
                }
                type="number"
                min="100"
                value={command.maximumPercent ?? ''}
              />
            </div>
          </div>

          {/* Bake time applies to both ROLLING and BLUE_GREEN. AWS ECS supports bakeTimeInMinutes for
              the ECS deployment controller regardless of strategy (it only excludes the EXTERNAL and
              CODE_DEPLOY controllers), so it is intentionally rendered outside the isBlueGreen block. */}
          <div className="form-group">
            <div className="col-md-5 sm-label-right">
              Bake Time (minutes) <HelpField id="ecs.native.bakeTimeInMinutes" />
            </div>
            <div className="col-md-2">
              <input
                aria-label="Bake time in minutes"
                className="form-control input-sm no-spel"
                data-test-id="NativeDeployment.bakeTimeInMinutes"
                onChange={(event) =>
                  onFieldChange('bakeTimeInMinutes', event.target.value === '' ? null : Number(event.target.value))
                }
                type="number"
                value={command.bakeTimeInMinutes ?? ''}
              />
            </div>
            <div className="col-md-12">
              <span className="help-block" style={{ marginLeft: '0' }}>
                Minutes ECS waits before terminating the previous service revision and marking the deployment complete.
                For Blue/Green this is the soak after the new task set reaches steady state; for Rolling it is the soak
                after the new tasks are healthy, before the old revision is fully retired.
              </span>
            </div>
          </div>

          {isBlueGreen && (
            <>
              <div className="form-group">
                <div className="sm-label-left">
                  <b>Pause for approval (optional)</b> <HelpField id="ecs.native.lifecyclePause" />
                </div>
                <div className="col-md-12">
                  <span className="help-block" style={{ marginLeft: '0' }}>
                    ECS pauses the deployment at the chosen stage until a downstream &quot;Continue ECS Native Service
                    Deployment&quot; stage acts on it. Without one, ECS applies the timeout action by itself and this
                    deploy stage still reports success.
                  </span>
                </div>
              </div>
              <div className="form-group">
                <div className="col-md-5 sm-label-right">Pause stage</div>
                <div className="col-md-5">
                  <select
                    aria-label="Pause stage"
                    className="form-control input-sm"
                    data-test-id="NativeDeployment.lifecyclePauseStage"
                    onChange={(event) => onFieldChange('lifecyclePauseStage', event.target.value || null)}
                    value={command.lifecyclePauseStage || ''}
                  >
                    <option value="">No pause</option>
                    <option value="POST_SCALE_UP">After scale up</option>
                    <option value="POST_TEST_TRAFFIC_SHIFT">After test traffic shift</option>
                    <option value="PRE_PRODUCTION_TRAFFIC_SHIFT">Before production traffic shift</option>
                    <option value="POST_PRODUCTION_TRAFFIC_SHIFT">After production traffic shift</option>
                  </select>
                </div>
              </div>
              {command.lifecyclePauseStage && (
                <>
                  <div className="form-group">
                    <div className="col-md-5 sm-label-right">Pause timeout (minutes)</div>
                    <div className="col-md-2">
                      <input
                        aria-label="Pause timeout in minutes"
                        className="form-control input-sm no-spel"
                        data-test-id="NativeDeployment.lifecyclePauseTimeoutMinutes"
                        min="1"
                        onChange={(event) =>
                          onFieldChange(
                            'lifecyclePauseTimeoutMinutes',
                            event.target.value === '' ? null : Number(event.target.value),
                          )
                        }
                        type="number"
                        value={command.lifecyclePauseTimeoutMinutes ?? ''}
                      />
                    </div>
                    <div className="col-md-12">
                      <span className="help-block" style={{ marginLeft: '0' }}>
                        Must be longer than any Manual Judgment timeout in front of the Continue stage.
                      </span>
                    </div>
                  </div>
                  <div className="form-group">
                    <div className="col-md-5 sm-label-right">If nobody acts before the timeout</div>
                    <div className="col-md-5">
                      <select
                        aria-label="Pause timeout action"
                        className="form-control input-sm"
                        data-test-id="NativeDeployment.lifecyclePauseTimeoutAction"
                        onChange={(event) => onFieldChange('lifecyclePauseTimeoutAction', event.target.value)}
                        value={command.lifecyclePauseTimeoutAction || 'ROLLBACK'}
                      >
                        <option value="ROLLBACK">Roll back (safe default)</option>
                        <option value="CONTINUE">Continue</option>
                      </select>
                    </div>
                  </div>
                </>
              )}
              <div className="form-group">
                <div className="sm-label-left">
                  <b>ALB traffic shift {albShiftRequired ? '(required)' : '(optional)'}</b>{' '}
                  <HelpField id="ecs.native.blueGreenAdvanced" />
                </div>
                <div className="col-md-12">
                  {albShiftRequired ? (
                    <span className="help-block" style={{ marginLeft: '0' }}>
                      This service has a load balancer, so AWS ECS requires all four fields for a Blue/Green deploy and
                      rejects it otherwise. They route a separate ALB listener rule at the new task set for test traffic
                      before shifting production traffic over. To deploy without them, use the Rolling strategy.
                    </span>
                  ) : (
                    <span className="help-block" style={{ marginLeft: '0' }}>
                      Optional only because this service has no load balancer: ECS stands up a new task set with no
                      traffic to shift. If you attach a load balancer, AWS ECS requires all four fields for Blue/Green.
                    </span>
                  )}
                </div>
              </div>

              <div className="form-group">
                <div className="col-md-5 sm-label-right">Alternate target group ARN</div>
                <div className="col-md-7">
                  <input
                    aria-label="Alternate target group ARN"
                    className="form-control input-sm no-spel"
                    data-test-id="NativeDeployment.alternateTargetGroupArn"
                    onChange={(event) => onFieldChange('alternateTargetGroupArn', event.target.value)}
                    type="text"
                    value={command.alternateTargetGroupArn || ''}
                  />
                </div>
              </div>

              <div className="form-group">
                <div className="col-md-5 sm-label-right">Production listener rule ARN</div>
                <div className="col-md-7">
                  <input
                    aria-label="Production listener rule ARN"
                    className="form-control input-sm no-spel"
                    data-test-id="NativeDeployment.productionListenerRule"
                    onChange={(event) => onFieldChange('productionListenerRule', event.target.value)}
                    type="text"
                    value={command.productionListenerRule || ''}
                  />
                </div>
              </div>

              <div className="form-group">
                <div className="col-md-5 sm-label-right">Test listener rule ARN</div>
                <div className="col-md-7">
                  <input
                    aria-label="Test listener rule ARN"
                    className="form-control input-sm no-spel"
                    data-test-id="NativeDeployment.testListenerRule"
                    onChange={(event) => onFieldChange('testListenerRule', event.target.value)}
                    type="text"
                    value={command.testListenerRule || ''}
                  />
                </div>
              </div>

              <div className="form-group">
                <div className="col-md-5 sm-label-right">Blue/Green IAM role ARN</div>
                <div className="col-md-7">
                  <input
                    aria-label="Blue/Green IAM role ARN"
                    className="form-control input-sm no-spel"
                    data-test-id="NativeDeployment.blueGreenRoleArn"
                    onChange={(event) => onFieldChange('blueGreenRoleArn', event.target.value)}
                    type="text"
                    value={command.blueGreenRoleArn || ''}
                  />
                </div>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
};
