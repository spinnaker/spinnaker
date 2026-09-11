import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  PlatformHealthOverride,
  Registry,
  StageConfigField,
  StageConstants,
  TargetSelect,
} from '@spinnaker/core';

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

export function OracleResizeAsgStageConfig(props: IStageConfigProps) {
  const { application, pipeline, stage, stageFieldUpdated, updateStageField } = props;
  const [accounts, setAccounts] = useState<IAccount[]>([]);

  useEffect(() => {
    const changes: Record<string, any> = {};
    if (stage.cloudProvider !== 'oracle') {
      changes.cloudProvider = 'oracle';
      changes.cloudProviderType = 'oracle';
    }
    if (!stage.credentials && application.defaultCredentials?.oracle) {
      changes.credentials = application.defaultCredentials.oracle;
    }
    if (!stage.regions?.length && application.defaultRegions?.oracle) {
      changes.regions = [application.defaultRegions.oracle];
    }
    if (!stage.target) {
      changes.target = StageConstants.TARGET_LIST[0].val;
    }
    if (!stage.action) {
      changes.action = stage.resizeType === 'exact' ? 'scale_exact' : scaleActions[0].val;
    }
    if (!stage.resizeType) {
      changes.resizeType = resizeTypes[0].val;
    }
    if (!stage.capacity) {
      changes.capacity = {};
    }
    if (Object.keys(changes).length) {
      updateStageField(changes);
    }
  }, []);

  useEffect(() => {
    let active = true;
    AccountService.listAccounts('oracle').then((loadedAccounts) => active && setAccounts(loadedAccounts));
    return () => {
      active = false;
    };
  }, []);

  const updateResizeType = (action: string, resizeType = stage.resizeType || resizeTypes[0].val) => {
    if (action === 'scale_exact') {
      updateStageField({ action, resizeType: 'exact', scaleNum: undefined, scalePct: undefined });
      return;
    }
    const nextResizeType = resizeType === 'incr' ? 'incr' : 'pct';
    const changes: Record<string, any> = { action, capacity: {}, resizeType: nextResizeType };
    if (nextResizeType === 'pct') {
      changes.scaleNum = undefined;
    } else {
      changes.scalePct = undefined;
      changes.scaleNum = stage.scaleNum || 0;
    }
    updateStageField(changes);
  };

  return (
    <div className="form-horizontal">
      {!pipeline.strategy && (
        <AccountRegionClusterSelector
          accounts={accounts}
          application={application}
          component={stage}
          onComponentUpdate={stageFieldUpdated}
        />
      )}
      <StageConfigField label="Target">
        <TargetSelect
          model={{ target: stage.target }}
          onChange={(target: string) => updateStageField({ target })}
          options={StageConstants.TARGET_LIST}
        />
      </StageConfigField>
      <StageConfigField label="Action" helpKey="pipeline.config.resizeAsg.action">
        <select
          className="form-control input-sm"
          onChange={(event) => updateResizeType(event.target.value)}
          value={stage.action || scaleActions[0].val}
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
              onChange={(event) => updateResizeType(stage.action, event.target.value)}
              value={stage.resizeType || resizeTypes[0].val}
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
                min={0}
                onChange={(event) => updateStageField({ scalePct: Number(event.target.value) })}
                type="number"
                value={stage.scalePct ?? ''}
              />
              <div>
                <em className="subinput-note">
                  This is the percentage by which the target server group's capacity will be increased
                </em>
              </div>
            </StageConfigField>
          )}
          {stage.resizeType === 'incr' && (
            <StageConfigField label="Resize-by Amount">
              <input
                className="form-control input-sm"
                min={0}
                onChange={(event) => updateStageField({ scaleNum: Number(event.target.value) })}
                type="number"
                value={stage.scaleNum ?? ''}
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
          <StageConfigField label="">
            <div className="row small">
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
                    onChange={(event) =>
                      updateStageField({ capacity: { ...stage.capacity, [field]: Number(event.target.value) } })
                    }
                    type="number"
                    value={stage.capacity?.[field] ?? ''}
                  />
                </div>
              ))}
            </div>
          </StageConfigField>
        </>
      )}
      {application.attributes.platformHealthOnlyShowOverride && (
        <StageConfigField label="Task Completion">
          <PlatformHealthOverride
            interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
            onChange={(interestingHealthProviderNames) => updateStageField({ interestingHealthProviderNames })}
            platformHealthType="Oracle"
          />
        </StageConfigField>
      )}
    </div>
  );
}

export const oracleResizeAsgStage = {
  key: 'resizeServerGroup',
  provides: 'resizeServerGroup',
  cloudProvider: 'oracle',
  component: OracleResizeAsgStageConfig,
  validators: [
    {
      type: 'targetImpedance',
      message:
        'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
    },
    { type: 'requiredField', fieldName: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'action' },
    { type: 'requiredField', fieldName: 'regions' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerOracleResizeAsgStage() {
  Registry.pipeline.registerStage(oracleResizeAsgStage);
}

registerOracleResizeAsgStage();
