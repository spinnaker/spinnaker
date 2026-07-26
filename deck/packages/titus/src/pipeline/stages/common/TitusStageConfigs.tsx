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

const TITUS_HEALTH_PROVIDER = 'Titus';
const ADD_TO_LOAD_BALANCER = 'AddToLoadBalancer';

const scaleActions = [
  { label: 'Scale Up', val: 'scale_up' },
  { label: 'Scale Down', val: 'scale_down' },
  { label: 'Scale to Cluster Size', val: 'scale_to_cluster' },
  { label: 'Scale to Exact Size', val: 'scale_exact' },
];

const resizeTypes = [
  { label: 'Percentage', val: 'pct' },
  { label: 'Incremental', val: 'incr' },
];

const selectionStrategies = [
  {
    label: 'Largest',
    val: 'LARGEST',
    description: 'When multiple server groups exist, prefer the server group with the most instances',
  },
  { label: 'Newest', val: 'NEWEST', description: 'When multiple server groups exist, prefer the newest' },
  { label: 'Oldest', val: 'OLDEST', description: 'When multiple server groups exist, prefer the oldest' },
  { label: 'Fail', val: 'FAIL', description: 'When multiple server groups exist, fail' },
];

function useForceUpdate(): () => void {
  const [, forceUpdate] = React.useReducer((count) => count + 1, 0);
  return forceUpdate;
}

function useTitusAccounts(): IAccount[] {
  const [accounts, setAccounts] = React.useState<IAccount[]>([]);

  React.useEffect(() => {
    let mounted = true;
    AccountService.listAccounts('titus').then((titusAccounts: IAccount[]) => {
      if (mounted) {
        setAccounts(titusAccounts);
      }
    });
    return () => {
      mounted = false;
    };
  }, []);

  return accounts;
}

function applyTitusDefaults(
  props: IStageConfigProps,
  defaults: Record<string, any> = {},
  healthOverride = false,
): void {
  const { application, stage, updateStageField } = props;
  const changes: Record<string, any> = { cloudProvider: 'titus' };
  const defaultCredentials = application.defaultCredentials?.titus;
  const defaultRegion = application.defaultRegions?.titus;

  if (!stage.credentials && defaultCredentials) {
    changes.credentials = defaultCredentials;
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
    changes.interestingHealthProviderNames = [TITUS_HEALTH_PROVIDER];
  }

  updateStageField(changes);
}

function useTitusDefaults(props: IStageConfigProps, defaults: Record<string, any> = {}, healthOverride = false): void {
  React.useEffect(() => applyTitusDefaults(props, defaults, healthOverride), []);
}

function pluralize(str: string, val: string | number): string {
  return Number(val) === 1 ? str : `${str}s`;
}

function TitusClusterSelector(
  props: IStageConfigProps & { clusterField?: string; singleRegion?: boolean; showAllRegions?: boolean },
) {
  const accounts = useTitusAccounts();
  const { application, clusterField, pipeline, showAllRegions, singleRegion, stage } = props;

  if (pipeline.strategy) {
    return null;
  }

  return (
    <AccountRegionClusterSelector
      accounts={accounts}
      application={application}
      clusterField={clusterField}
      component={stage}
      showAllRegions={showAllRegions}
      singleRegion={singleRegion ? 'true' : undefined}
    />
  );
}

function TitusTargetSelect({ stage, updateStageField }: IStageConfigProps) {
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

function TitusHealthOverride({ application, stage, updateStageField }: IStageConfigProps) {
  if (!application.attributes.platformHealthOnlyShowOverride) {
    return null;
  }

  return (
    <StageConfigField label="Task Completion">
      <PlatformHealthOverride
        interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
        onChange={(interestingHealthProviderNames) => updateStageField({ interestingHealthProviderNames })}
        platformHealthType={TITUS_HEALTH_PROVIDER}
      />
    </StageConfigField>
  );
}

function setNestedStageField(props: IStageConfigProps, field: string, value: any, forceUpdate: () => void): void {
  set(props.stage, field, value);
  props.stageFieldUpdated();
  forceUpdate();
}

function TitusServerGroupTargetStageConfig(props: IStageConfigProps & { healthOverride?: boolean }) {
  useTitusDefaults(props, { target: StageConstants.TARGET_LIST[0].val }, props.healthOverride);

  return (
    <div className="form-horizontal">
      <TitusClusterSelector {...props} />
      <TitusTargetSelect {...props} />
      {props.healthOverride && <TitusHealthOverride {...props} />}
    </div>
  );
}

export function TitusDestroyAsgStageConfig(props: IStageConfigProps) {
  return <TitusServerGroupTargetStageConfig {...props} />;
}

export function TitusDisableAsgStageConfig(props: IStageConfigProps) {
  return <TitusServerGroupTargetStageConfig {...props} healthOverride />;
}

export function TitusEnableAsgStageConfig(props: IStageConfigProps) {
  return <TitusServerGroupTargetStageConfig {...props} healthOverride />;
}

export function TitusDisableClusterStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  useTitusDefaults(props, { preferLargerOverNewer: 'false', remainingEnabledServerGroups: 1 }, true);

  return (
    <div className="form-horizontal">
      <TitusClusterSelector {...props} />
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
      <TitusHealthOverride {...props} />
    </div>
  );
}

export function TitusFindImageStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  useTitusDefaults(props, { onlyEnabled: true, selectionStrategy: selectionStrategies[0].val });

  return (
    <div className="form-horizontal">
      <TitusClusterSelector {...props} showAllRegions />
      <StageConfigField label="Server Group Selection">
        <select
          className="form-control input-sm"
          onChange={(event) => updateStageField({ selectionStrategy: event.target.value })}
          required
          value={stage.selectionStrategy ?? selectionStrategies[0].val}
        >
          {selectionStrategies.map((strategy) => (
            <option key={strategy.val} value={strategy.val}>
              {strategy.label}
            </option>
          ))}
        </select>
      </StageConfigField>
      <StageConfigField label="Server Group Filters">
        <label className="checkbox-inline">
          <input
            checked={stage.onlyEnabled ?? true}
            onChange={(event) => updateStageField({ onlyEnabled: event.target.checked })}
            type="checkbox"
          />{' '}
          Only consider enabled Server Groups
        </label>
      </StageConfigField>
    </div>
  );
}

export function TitusScaleDownClusterStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  useTitusDefaults(props, {
    allowScaleDownActive: false,
    preferLargerOverNewer: 'false',
    remainingFullSizeServerGroups: 1,
  });

  return (
    <div className="form-horizontal">
      <TitusClusterSelector {...props} />
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

export function TitusShrinkClusterStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  useTitusDefaults(props, { allowDeleteActive: false, retainLargerOverNewer: 'false', shrinkToSize: 1 }, true);

  return (
    <div className="form-horizontal">
      <TitusClusterSelector {...props} />
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
      <TitusHealthOverride {...props} />
    </div>
  );
}

export function TitusCloneServerGroupStageConfig(props: IStageConfigProps) {
  const { stage, stageFieldUpdated, updateStageField } = props;
  const forceUpdate = useForceUpdate();
  const capacity = stage.capacity || { desired: 1, max: 1, min: 1 };
  const suspendedProcesses = stage.suspendedProcesses || [];
  const addToLoadBalancerSuspended = suspendedProcesses.includes(ADD_TO_LOAD_BALANCER);
  useTitusDefaults(
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
      <TitusClusterSelector {...props} clusterField="targetCluster" singleRegion />
      <TitusTargetSelect {...props} />
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
              min="0"
              onChange={(event) => updateCapacity(field, Number(event.target.value))}
              required
              type="number"
              value={capacity[field] ?? 1}
            />
          </div>
        ))}
      </div>
      <StageConfigField label="Traffic" helpKey="titus.serverGroup.traffic">
        <div className="checkbox">
          <label>
            <input checked={!addToLoadBalancerSuspended} onChange={toggleAddToLoadBalancer} type="checkbox" /> Send
            client requests to new instances
          </label>
        </div>
      </StageConfigField>
      <TitusHealthOverride {...props} />
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

export function TitusResizeAsgStageConfig(props: IStageConfigProps) {
  const { stage, updateStageField } = props;
  const capacity = stage.capacity || {};
  useTitusDefaults(
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
      <TitusClusterSelector {...props} />
      <TitusTargetSelect {...props} />
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
            <div className="form-group">
              <div className="col-md-9 col-md-offset-3">
                <label className="col-md-2 sm-label-right" style={{ marginLeft: 0, paddingLeft: 0 }}>
                  Resize Percentage
                </label>
                <div className="col-md-2">
                  <input
                    className="form-control input-sm"
                    min="0"
                    onChange={(event) => updateStageField({ scalePct: Number(event.target.value) })}
                    type="number"
                    value={stage.scalePct ?? 0}
                  />
                </div>
              </div>
              <div className="col-md-9 col-md-offset-3">
                <em className="subinput-note">
                  This is the percentage by which the target server group's capacity will be increased
                </em>
              </div>
            </div>
          )}
          {stage.resizeType !== 'pct' && (
            <div className="form-group">
              <div className="col-md-9 col-md-offset-3">
                <label className="col-md-2 sm-label-right" style={{ marginLeft: 0, paddingLeft: 0 }}>
                  Resize-by Amount
                </label>
                <div className="col-md-2">
                  <input
                    className="form-control input-sm"
                    min="0"
                    onChange={(event) => updateStageField({ scaleNum: Number(event.target.value) })}
                    type="number"
                    value={stage.scaleNum ?? 0}
                  />
                </div>
              </div>
              <div className="col-md-9 col-md-offset-3">
                <em className="subinput-note">
                  This is the exact amount by which the target server group's capacity will be increased
                </em>
              </div>
            </div>
          )}
        </>
      )}
      {stage.action === 'scale_exact' && (
        <div className="form-group">
          <div className="col-md-9 col-md-offset-3 small">
            <div className="col-md-9">
              <div className="col-md-3 col-md-offset-3">Min</div>
              <div className="col-md-3">Max</div>
              <div className="col-md-3">Desired</div>
            </div>
          </div>
          <div className="col-md-9 col-md-offset-3">
            <label className="col-md-2 sm-label-right small" style={{ marginLeft: 0, paddingLeft: 0 }}>
              Match Capacity
            </label>
            <div className="col-md-9">
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
          </div>
          <div className="col-md-9 col-md-offset-3">
            <em className="subinput-note">This is the exact amount to which the target server group will be scaled</em>
          </div>
        </div>
      )}
      <TitusHealthOverride {...props} />
    </div>
  );
}
