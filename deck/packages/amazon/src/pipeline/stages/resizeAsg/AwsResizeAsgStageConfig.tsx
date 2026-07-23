import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  PlatformHealthOverride,
  StageConfigField,
  StageConstants,
  TargetSelect,
} from '@spinnaker/core';

const scaleActions = [
  { label: 'Scale Up', value: 'scale_up' },
  { label: 'Scale Down', value: 'scale_down' },
  { label: 'Scale to Cluster Size', value: 'scale_to_cluster' },
  { label: 'Scale to Exact Size', value: 'scale_exact' },
];

const resizeTypes = [
  { label: 'Percentage', value: 'pct' },
  { label: 'Incremental', value: 'incr' },
];

const integerPattern = String.raw`(?:\$\{.+\}|\d+)`;

function isExpression(value: unknown): value is string {
  return typeof value === 'string' && /^\$\{[\s\S]+\}$/.test(value);
}

function isValidNumericValue(value: unknown, integer: boolean, maximum?: number): boolean {
  if (isExpression(value)) {
    return true;
  }

  if (typeof value !== 'number' && typeof value !== 'string') {
    return false;
  }

  if (typeof value === 'string' && !(integer ? /^\d+$/ : /^(?:\d+(?:\.\d*)?|\.\d+)$/.test(value))) {
    return false;
  }

  const numericValue = Number(value);
  return (
    Number.isFinite(numericValue) &&
    numericValue >= 0 &&
    (!integer || Number.isInteger(numericValue)) &&
    (maximum === undefined || numericValue <= maximum)
  );
}

function parseNumericValue(value: string, integer: boolean): number | string {
  return isExpression(value) || !isValidNumericValue(value, integer) ? value : Number(value);
}

interface IResizeAsgStage {
  action?: string;
  capacity?: {
    desired?: number | string | null;
    max?: number | string | null;
    min?: number | string | null;
  };
  resizeType?: string;
  scaleNum?: number | string | null;
  scalePct?: number | string | null;
  targetHealthyDeployPercentage?: number | string | null;
}

export function validateAwsResizeAsgStage(stage: IResizeAsgStage): string {
  if (stage.action === 'scale_exact') {
    const capacityFields = [
      ['min', 'Minimum'],
      ['max', 'Maximum'],
      ['desired', 'Desired'],
    ] as const;

    for (const [field, label] of capacityFields) {
      if (!isValidNumericValue(stage.capacity?.[field], true)) {
        return `${label} capacity must be a nonnegative integer or pipeline expression.`;
      }
    }
  } else if (stage.resizeType === 'pct' && !isValidNumericValue(stage.scalePct, true)) {
    return 'Resize percentage must be a nonnegative integer or pipeline expression.';
  } else if (stage.resizeType === 'incr' && !isValidNumericValue(stage.scaleNum, true)) {
    return 'Resize count must be a nonnegative integer or pipeline expression.';
  }

  if (
    (stage.action === 'scale_up' || stage.action === 'scale_to_cluster') &&
    !isValidNumericValue(stage.targetHealthyDeployPercentage, true, 100)
  ) {
    return 'Target healthy percentage must be an integer from 0 through 100 or pipeline expression.';
  }

  return '';
}

interface IControlledIntegerInputProps {
  dataCapacityField?: string;
  maximum?: number;
  name: string;
  onValidChange: (value: number | string) => void;
  resetKey?: string | number;
  value: number | string | null | undefined;
}

function ControlledIntegerInput({
  dataCapacityField,
  maximum,
  name,
  onValidChange,
  resetKey,
  value,
}: IControlledIntegerInputProps) {
  const [rawValue, setRawValue] = useState(value ?? '');

  useEffect(() => setRawValue(value ?? ''), [resetKey, value]);

  const valid = isValidNumericValue(rawValue, true, maximum);
  const onChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const nextValue = event.target.value;
    setRawValue(nextValue);
    if (isValidNumericValue(nextValue, true, maximum)) {
      onValidChange(parseNumericValue(nextValue, true));
    }
  };

  return (
    <input
      aria-invalid={!valid}
      className="form-control input-sm"
      data-capacity-field={dataCapacityField}
      inputMode="numeric"
      name={name}
      onChange={onChange}
      pattern={integerPattern}
      required
      value={rawValue}
    />
  );
}

export function AwsResizeAsgStageConfig(props: IStageConfigProps) {
  const { application, pipeline, stage, stageFieldUpdated, updateStage, updateStageField } = props;
  const [accounts, setAccounts] = useState<IAccount[]>([]);

  useEffect(() => {
    const changes: Record<string, any> = { cloudProvider: 'aws' };
    const defaults = {
      action: stage.resizeType === 'exact' ? 'scale_exact' : scaleActions[0].value,
      capacity: {},
      resizeType: resizeTypes[0].value,
      target: StageConstants.TARGET_LIST[0].val,
      targetHealthyDeployPercentage: 100,
    };

    Object.entries(defaults).forEach(([field, value]) => {
      if (stage[field] === undefined) {
        changes[field] = value;
      }
    });

    if (!stage.credentials && application.defaultCredentials?.aws) {
      changes.credentials = application.defaultCredentials.aws;
    }

    if (!stage.regions?.length) {
      changes.regions = application.defaultRegions?.aws ? [application.defaultRegions.aws] : [];
    }

    if (
      stage.isNew &&
      stage.interestingHealthProviderNames === undefined &&
      application.attributes.platformHealthOnlyShowOverride &&
      application.attributes.platformHealthOnly
    ) {
      changes.interestingHealthProviderNames = ['Amazon'];
    }

    updateStageField(changes);
  }, []);

  useEffect(() => {
    let active = true;
    AccountService.listAccounts('aws').then((loadedAccounts) => active && setAccounts(loadedAccounts));
    return () => {
      active = false;
    };
  }, []);

  const updateResizeType = (action: string, resizeType = stage.resizeType || resizeTypes[0].value) => {
    if (action === 'scale_exact') {
      updateStage({ action, resizeType: 'exact', scaleNum: undefined, scalePct: undefined });
      return;
    }

    const nextResizeType = resizeType === 'incr' ? 'incr' : 'pct';
    const changes: Record<string, any> = {
      action,
      capacity: {},
      resizeType: nextResizeType,
      scaleNum: undefined,
      scalePct: undefined,
    };
    if (nextResizeType === 'pct') {
      changes.scalePct = stage.scalePct || 0;
    } else {
      changes.scaleNum = stage.scaleNum || 0;
    }
    updateStage(changes);
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
          options={StageConstants.TARGET_LIST}
          onChange={(target: string) => updateStageField({ target })}
        />
      </StageConfigField>
      <StageConfigField label="Action" helpKey="pipeline.config.resizeAsg.action">
        <select
          className="form-control input-sm"
          name="action"
          onChange={(event) => updateResizeType(event.target.value)}
          required
          value={stage.action}
        >
          {scaleActions.map((action) => (
            <option key={action.value} value={action.value}>
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
              name="resizeType"
              onChange={(event) => updateResizeType(stage.action, event.target.value)}
              required
              value={stage.resizeType}
            >
              {resizeTypes.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
          </StageConfigField>
          {stage.resizeType === 'pct' && (
            <StageConfigField label="Resize Percentage">
              <ControlledIntegerInput
                name="scalePct"
                onValidChange={(scalePct) => updateStageField({ scalePct })}
                resetKey={stage.refId}
                value={stage.scalePct}
              />
            </StageConfigField>
          )}
          {stage.resizeType === 'incr' && (
            <StageConfigField label="Resize Count">
              <ControlledIntegerInput
                name="scaleNum"
                onValidChange={(scaleNum) => updateStageField({ scaleNum })}
                resetKey={stage.refId}
                value={stage.scaleNum}
              />
            </StageConfigField>
          )}
          {(stage.action === 'scale_up' || stage.action === 'scale_to_cluster') && (
            <StageConfigField label="Target Healthy Percentage">
              <ControlledIntegerInput
                maximum={100}
                name="targetHealthyDeployPercentage"
                onValidChange={(targetHealthyDeployPercentage) => updateStageField({ targetHealthyDeployPercentage })}
                resetKey={stage.refId}
                value={stage.targetHealthyDeployPercentage}
              />
            </StageConfigField>
          )}
        </>
      )}
      {stage.action === 'scale_exact' && (
        <>
          <StageConfigField label="Capacity">
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
                  <ControlledIntegerInput
                    dataCapacityField={field}
                    name={`capacity.${field}`}
                    onValidChange={(value) =>
                      updateStageField({
                        capacity: { ...stage.capacity, [field]: value },
                      })
                    }
                    resetKey={stage.refId}
                    value={stage.capacity?.[field]}
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
      {application.attributes.platformHealthOnlyShowOverride && (
        <StageConfigField label="Task Completion">
          <PlatformHealthOverride
            interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
            onChange={(interestingHealthProviderNames) => updateStageField({ interestingHealthProviderNames })}
            platformHealthType="Amazon"
          />
        </StageConfigField>
      )}
    </div>
  );
}
