import { set } from 'lodash';
import React from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  DeploymentStrategySelector,
  HelpField,
  PlatformHealthOverride,
  StageConfigField,
  StageConstants,
  TargetSelect,
} from '@spinnaker/core';

const ECS_HEALTH_PROVIDER = 'Ecs';
const ADD_TO_LOAD_BALANCER = 'AddToLoadBalancer';

const scaleActions = [
  { label: 'Scale by percentage or number', val: 'scale' },
  { label: 'Scale to an exact size', val: 'scale_exact' },
  { label: 'Scale to cluster size', val: 'scale_to_cluster' },
];

const resizeTypes = [
  { label: 'Scale by number of instances', val: 'incr' },
  { label: 'Scale by percentage', val: 'pct' },
];

function useForceUpdate(): () => void {
  const [, setCounter] = React.useState(0);
  return () => setCounter((counter) => counter + 1);
}

function useEcsAccounts(): IAccount[] {
  const [accounts, setAccounts] = React.useState<IAccount[]>([]);

  React.useEffect(() => {
    let active = true;
    AccountService.listAccounts('ecs').then((loadedAccounts) => active && setAccounts(loadedAccounts));
    return () => {
      active = false;
    };
  }, []);

  return accounts;
}

function applyEcsDefaults(props: IStageConfigProps, defaults: Record<string, any> = {}, healthOverride = false): void {
  const { application, stage, updateStageField } = props;
  const changes: Record<string, any> = { cloudProvider: 'ecs', cloudProviderType: 'ecs' };
  const defaultCredentials = application.defaultCredentials?.ecs;
  const defaultRegion = application.defaultRegions?.ecs;

  if (!stage.credentials && defaultCredentials) {
    changes.credentials = defaultCredentials;
  }

  if (!stage.region && defaultRegion) {
    changes.region = defaultRegion;
  }

  if (!stage.regions?.length) {
    changes.regions = defaultRegion ? [defaultRegion] : [];
  }

  Object.keys(defaults).forEach((key) => {
    if (stage[key] === undefined) {
      changes[key] = defaults[key];
    }
  });

  if (
    healthOverride &&
    stage.isNew &&
    stage.interestingHealthProviderNames === undefined &&
    application.attributes.platformHealthOnlyShowOverride &&
    application.attributes.platformHealthOnly
  ) {
    changes.interestingHealthProviderNames = [ECS_HEALTH_PROVIDER];
  }

  updateStageField(changes);
}

function useEcsDefaults(props: IStageConfigProps, defaults: Record<string, any> = {}, healthOverride = false): void {
  React.useEffect(() => applyEcsDefaults(props, defaults, healthOverride), []);
}

function pluralize(str: string, val: string | number): string {
  return Number(val) === 1 ? str : `${str}s`;
}

function EcsClusterSelector(
  props: IStageConfigProps & { clusterField?: string; singleRegion?: boolean; showAllRegions?: boolean },
) {
  const accounts = useEcsAccounts();
  const { application, clusterField, pipeline, showAllRegions, singleRegion, stage, stageFieldUpdated } = props;

  if (pipeline.strategy) {
    return null;
  }

  return (
    <AccountRegionClusterSelector
      accounts={accounts}
      application={application}
      clusterField={clusterField}
      component={stage}
      onComponentUpdate={stageFieldUpdated}
      showAllRegions={showAllRegions}
      singleRegion={singleRegion ? 'true' : undefined}
    />
  );
}

function EcsTargetSelect({ stage, updateStageField }: IStageConfigProps) {
  return (
    <StageConfigField label="Target">
      <TargetSelect
        model={{ target: stage.target }}
        options={StageConstants.TARGET_LIST}
        onChange={(target: string) => updateStageField({ target })}
      />
    </StageConfigField>
  );
}

function EcsHealthOverride({ application, stage, updateStageField }: IStageConfigProps) {
  if (!application.attributes.platformHealthOnlyShowOverride) {
    return null;
  }

  return (
    <StageConfigField label="Task Completion">
      <PlatformHealthOverride
        interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
        onChange={(interestingHealthProviderNames) => updateStageField({ interestingHealthProviderNames })}
        platformHealthType={ECS_HEALTH_PROVIDER}
      />
    </StageConfigField>
  );
}

function setNestedStageField(props: IStageConfigProps, field: string, value: any, forceUpdate: () => void): void {
  set(props.stage, field, value);
  props.stageFieldUpdated();
  forceUpdate();
}

function EcsServerGroupTargetStageConfig(props: IStageConfigProps & { healthOverride?: boolean }) {
  useEcsDefaults(props, { target: StageConstants.TARGET_LIST[0].val }, props.healthOverride);

  return (
    <div className="form-horizontal">
      <EcsClusterSelector {...props} />
      <EcsTargetSelect {...props} />
      {props.healthOverride && <EcsHealthOverride {...props} />}
    </div>
  );
}

export function EcsDestroyAsgStageConfig(props: IStageConfigProps) {
  return <EcsServerGroupTargetStageConfig {...props} />;
}

export function EcsDisableAsgStageConfig(props: IStageConfigProps) {
  return <EcsServerGroupTargetStageConfig {...props} healthOverride />;
}

export function EcsEnableAsgStageConfig(props: IStageConfigProps) {
  return <EcsServerGroupTargetStageConfig {...props} healthOverride />;
}

export function EcsDisableClusterStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  useEcsDefaults(props, { preferLargerOverNewer: 'false', remainingEnabledServerGroups: 1 }, true);

  return (
    <div className="form-horizontal">
      <EcsClusterSelector {...props} />
      <StageConfigField label="Disable Options">
        <div className="form-inline">
          Keep the{' '}
          <input
            className="form-control input-sm"
            min="0"
            onChange={(event) => updateStageField({ remainingEnabledServerGroups: Number(event.target.value) })}
            required
            style={{ width: '50px' }}
            type="number"
            value={stage.remainingEnabledServerGroups ?? 1}
          />{' '}
          <select
            className="form-control input-sm"
            onChange={(event) => updateStageField({ preferLargerOverNewer: event.target.value })}
            style={{ width: '100px' }}
            value={stage.preferLargerOverNewer ?? 'false'}
          >
            <option value="true">largest</option>
            <option value="false">newest</option>
          </select>{' '}
          {pluralize('server group', stage.remainingEnabledServerGroups ?? 1)} enabled.
        </div>
      </StageConfigField>
      <EcsHealthOverride {...props} />
    </div>
  );
}

export function EcsFindImageFromTagsStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  useEcsDefaults(props);

  return (
    <div className="form-horizontal">
      <StageConfigField label="Full Docker Image path (label or SHA-256)">
        <input
          className="form-control input-sm"
          onChange={(event) => updateStageField({ imageLabelOrSha: event.target.value })}
          type="text"
          value={stage.imageLabelOrSha || ''}
        />
      </StageConfigField>
      <div>
        <h5>
          Example value: <i>12345678901.dkr.ecr.us-west-2.amazonaws.com/continuous-delivery:latest</i>
        </h5>
      </div>
    </div>
  );
}

export function EcsScaleDownClusterStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  useEcsDefaults(props, {
    allowScaleDownActive: false,
    preferLargerOverNewer: 'false',
    remainingFullSizeServerGroups: 1,
  });

  return (
    <div className="form-horizontal">
      <EcsClusterSelector {...props} />
      <StageConfigField label="Scale Down Options">
        <div className="form-inline">
          <p>
            Keep the{' '}
            <input
              className="form-control input-sm"
              min="0"
              onChange={(event) => updateStageField({ remainingFullSizeServerGroups: Number(event.target.value) })}
              required
              style={{ width: '50px' }}
              type="number"
              value={stage.remainingFullSizeServerGroups ?? 1}
            />{' '}
            <select
              className="form-control input-sm"
              onChange={(event) => updateStageField({ preferLargerOverNewer: event.target.value })}
              style={{ width: '100px' }}
              value={stage.preferLargerOverNewer ?? 'false'}
            >
              <option value="true">largest</option>
              <option value="false">newest</option>
            </select>{' '}
            {pluralize('server group', stage.remainingFullSizeServerGroups ?? 1)} at current size.
          </p>
          <p>The remaining server groups will be scaled down to zero instances.</p>
        </div>
      </StageConfigField>
      <div className="form-group">
        <div className="col-md-offset-3 col-md-6 checkbox">
          <label>
            <input
              checked={Boolean(stage.allowScaleDownActive)}
              onChange={(event) => updateStageField({ allowScaleDownActive: event.target.checked })}
              type="checkbox"
            />{' '}
            Allow scale down of active server groups
          </label>
        </div>
      </div>
    </div>
  );
}

export function EcsShrinkClusterStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  useEcsDefaults(props, { allowDeleteActive: false, retainLargerOverNewer: 'false', shrinkToSize: 1 }, true);

  return (
    <div className="form-horizontal">
      <EcsClusterSelector {...props} />
      <StageConfigField label="Shrink Options">
        <div className="form-inline">
          Shrink to{' '}
          <input
            className="form-control input-sm"
            min="0"
            onChange={(event) => updateStageField({ shrinkToSize: Number(event.target.value) })}
            required
            style={{ width: '50px' }}
            type="number"
            value={stage.shrinkToSize ?? 1}
          />{' '}
          {pluralize('server group', stage.shrinkToSize ?? 1)}, keeping the{' '}
          <select
            className="form-control input-sm"
            onChange={(event) => updateStageField({ retainLargerOverNewer: event.target.value })}
            style={{ width: '100px' }}
            value={stage.retainLargerOverNewer ?? 'false'}
          >
            <option value="true">largest</option>
            <option value="false">newest</option>
          </select>
        </div>
      </StageConfigField>
      <div className="form-group">
        <div className="col-md-offset-3 col-md-6 checkbox">
          <label>
            <input
              checked={Boolean(stage.allowDeleteActive)}
              onChange={(event) => updateStageField({ allowDeleteActive: event.target.checked })}
              type="checkbox"
            />{' '}
            Allow deletion of active server groups
          </label>
        </div>
      </div>
      <EcsHealthOverride {...props} />
    </div>
  );
}

export function EcsCloneServerGroupStageConfig(props: IStageConfigProps) {
  const { stage, stageFieldUpdated, updateStageField } = props;
  const forceUpdate = useForceUpdate();
  const capacity = stage.capacity || { desired: 1, max: 1, min: 1 };
  const suspendedProcesses = stage.suspendedProcesses || [];
  const addToLoadBalancerSuspended = suspendedProcesses.includes(ADD_TO_LOAD_BALANCER);
  useEcsDefaults(
    props,
    {
      capacity,
      suspendedProcesses,
      target: StageConstants.TARGET_LIST[0].val,
      useSourceCapacity: true,
    },
    true,
  );

  const updateCapacity = (field: string, value: number) => {
    updateStageField({ capacity: { ...capacity, [field]: value } });
  };

  const setUseSourceCapacity = (value: boolean) => {
    if (value) {
      delete stage.capacity;
      stageFieldUpdated();
      forceUpdate();
    } else if (!stage.capacity) {
      updateStageField({ capacity });
    }
    updateStageField({ useSourceCapacity: value });
  };

  const toggleAddToLoadBalancer = () => {
    updateStageField({
      suspendedProcesses: addToLoadBalancerSuspended
        ? suspendedProcesses.filter((process: string) => process !== ADD_TO_LOAD_BALANCER)
        : [...suspendedProcesses, ADD_TO_LOAD_BALANCER],
    });
  };

  const deploymentFieldChanged = (key: string, value: any) => setNestedStageField(props, key, value, forceUpdate);

  return (
    <div className="form-horizontal">
      <EcsClusterSelector {...props} clusterField="targetCluster" singleRegion />
      <EcsTargetSelect {...props} />
      <div className="form-group">
        <div className="col-md-3 sm-label-right">Capacity</div>
        <div className="col-md-9 radio">
          <label>
            <input
              checked={stage.useSourceCapacity !== false}
              id="useSourceCapacityTrue"
              onChange={() => setUseSourceCapacity(true)}
              type="radio"
            />{' '}
            Copy the capacity from the current server group <HelpField id="serverGroupCapacity.useSourceCapacityTrue" />
          </label>
        </div>
        <div className="col-md-9 col-md-offset-3 radio">
          <label>
            <input
              checked={stage.useSourceCapacity === false}
              id="useSourceCapacityFalse"
              onChange={() => setUseSourceCapacity(false)}
              type="radio"
            />{' '}
            Let me specify the capacity <HelpField id="serverGroupCapacity.useSourceCapacityFalse" />
          </label>
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-2 col-md-offset-3">Min</div>
        <div className="col-md-2">Max</div>
        <div className="col-md-2">Desired</div>
      </div>
      <div className="form-group">
        {(['min', 'max', 'desired'] as const).map((field) => (
          <div className={field === 'min' ? 'col-md-2 col-md-offset-3' : 'col-md-2'} key={field}>
            <input
              className="form-control input-sm"
              disabled={stage.useSourceCapacity !== false}
              max={field === 'min' || field === 'desired' ? capacity.max : undefined}
              min={field === 'max' ? capacity.min : 0}
              onChange={(event) => updateCapacity(field, Number(event.target.value))}
              required
              type="number"
              value={capacity[field] ?? 1}
            />
          </div>
        ))}
      </div>
      <StageConfigField label="Traffic">
        <div className="checkbox">
          <label>
            <input checked={!addToLoadBalancerSuspended} onChange={toggleAddToLoadBalancer} type="checkbox" /> Send
            client requests to new instances
          </label>
        </div>
      </StageConfigField>
      <EcsHealthOverride {...props} />
      <DeploymentStrategySelector
        command={stage as any}
        labelColumns="3"
        fieldColumns="6"
        onFieldChange={deploymentFieldChanged}
        onStrategyChange={(command) => updateStageField({ strategy: command.strategy })}
      />
    </div>
  );
}

export function EcsResizeAsgStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  const capacity = stage.capacity || {};
  useEcsDefaults(
    props,
    {
      action: scaleActions[0].val,
      capacity,
      resizeType: resizeTypes[0].val,
      target: StageConstants.TARGET_LIST[0].val,
      targetHealthyDeployPercentage: 100,
    },
    true,
  );

  const updateResizeType = (action: string, resizeType = stage.resizeType || resizeTypes[0].val) => {
    if (action === 'scale_exact') {
      delete stage.scaleNum;
      delete stage.scalePct;
      updateStageField({ action, resizeType: 'exact' });
      return;
    }

    const nextResizeType = resizeType === 'pct' ? 'pct' : 'incr';
    const changes: Record<string, any> = { action, capacity: {}, resizeType: nextResizeType };
    if (nextResizeType === 'pct') {
      delete stage.scaleNum;
    } else {
      delete stage.scalePct;
      changes.scaleNum = stage.scaleNum || 0;
    }
    updateStageField(changes);
  };

  const updateCapacity = (field: string, value: number) => {
    updateStageField({ capacity: { ...capacity, [field]: value } });
  };

  return (
    <div className="form-horizontal">
      <EcsClusterSelector {...props} />
      <EcsTargetSelect {...props} />
      <StageConfigField label="Action" helpKey="pipeline.config.resizeAsg.action">
        <select
          className="form-control input-sm"
          onChange={(event) => updateResizeType(event.target.value)}
          required
          value={stage.action ?? scaleActions[0].val}
        >
          {scaleActions.map((action) => (
            <option key={action.val} value={action.val}>
              {action.label}
            </option>
          ))}
        </select>
      </StageConfigField>
      {stage.action !== 'scale_exact' && (
        <>
          <StageConfigField label={stage.action === 'scale_to_cluster' ? 'Additional Capacity' : 'Type'}>
            <select
              className="form-control input-sm"
              onChange={(event) => updateResizeType(stage.action ?? scaleActions[0].val, event.target.value)}
              required
              value={stage.resizeType === 'exact' ? resizeTypes[0].val : stage.resizeType ?? resizeTypes[0].val}
            >
              {resizeTypes.map((type) => (
                <option key={type.val} value={type.val}>
                  {type.label}
                </option>
              ))}
            </select>
          </StageConfigField>
          {stage.resizeType === 'pct' && (
            <StageConfigField label="Resize Percentage">
              <input
                className="form-control input-sm"
                min="0"
                onChange={(event) => updateStageField({ scalePct: Number(event.target.value) })}
                style={{ width: '80px' }}
                type="number"
                value={stage.scalePct ?? 0}
              />
              <div>
                <em className="subinput-note">
                  This is the percentage by which the target server group's capacity will be increased
                </em>
              </div>
            </StageConfigField>
          )}
          {stage.resizeType !== 'pct' && (
            <StageConfigField label="Resize-by Amount">
              <input
                className="form-control input-sm"
                min="0"
                onChange={(event) => updateStageField({ scaleNum: Number(event.target.value) })}
                style={{ width: '80px' }}
                type="number"
                value={stage.scaleNum ?? 0}
              />
              <div>
                <em className="subinput-note">
                  This is the exact amount by which the target server group's capacity will be increased
                </em>
              </div>
            </StageConfigField>
          )}
        </>
      )}
      {stage.action === 'scale_exact' && (
        <>
          <StageConfigField label="Capacity">
            <div className="row">
              <div className="col-md-3">Min</div>
              <div className="col-md-3">Max</div>
              <div className="col-md-3">Desired</div>
            </div>
          </StageConfigField>
          <StageConfigField label="Match Capacity">
            <div className="row">
              {(['min', 'max', 'desired'] as const).map((field) => (
                <div className="col-md-3" key={field}>
                  <input
                    className="form-control input-sm"
                    onChange={(event) => updateCapacity(field, Number(event.target.value))}
                    type="number"
                    value={capacity[field] ?? 0}
                  />
                </div>
              ))}
            </div>
          </StageConfigField>
          <StageConfigField label="">
            <em className="subinput-note">This is the exact amount to which the target server group will be scaled</em>
          </StageConfigField>
        </>
      )}
      <EcsHealthOverride {...props} />
    </div>
  );
}
