import { get, set } from 'lodash';
import React, { useEffect, useState } from 'react';

import type { IAccount, IStageConfigProps } from '@spinnaker/core';
import {
  AccountRegionClusterSelector,
  AccountService,
  AppListExtractor,
  DeploymentStrategySelector,
  HelpField,
  NameUtils,
  PlatformHealthOverride,
  Registry,
  StageConfigField,
  StageConstants,
  TargetSelect,
} from '@spinnaker/core';

const capacityFields = ['min', 'max', 'desired'] as const;

export function AwsCloneServerGroupStageConfig(props: IStageConfigProps) {
  const { application, pipeline, stage, stageFieldUpdated, updateStageField } = props;
  const [accounts, setAccounts] = useState<IAccount[]>([]);

  useEffect(() => {
    const changes: Record<string, any> = {};
    if (!stage.application) {
      changes.application = application.name;
    }
    if (stage.cloudProvider !== 'aws') {
      changes.cloudProvider = 'aws';
    }
    if (stage.cloudProviderType !== 'aws') {
      changes.cloudProviderType = 'aws';
    }
    if (!stage.credentials && application.defaultCredentials?.aws) {
      changes.credentials = application.defaultCredentials.aws;
    }
    if (stage.target === undefined) {
      changes.target = StageConstants.TARGET_LIST[0].val;
    }
    if (stage.useSourceCapacity === undefined) {
      changes.useSourceCapacity = true;
    }
    if (
      stage.isNew &&
      application.attributes.platformHealthOnlyShowOverride &&
      application.attributes.platformHealthOnly &&
      stage.interestingHealthProviderNames === undefined
    ) {
      changes.interestingHealthProviderNames = ['Amazon'];
    }
    if (stage.isNew) {
      changes.useAmiBlockDeviceMappings = get(
        application,
        'attributes.providerSettings.aws.useAmiBlockDeviceMappings',
        false,
      );
      changes.copySourceCustomBlockDeviceMappings = false;
    }
    if (Object.keys(changes).length) {
      updateStageField(changes);
    }
  }, []);

  useEffect(() => {
    let active = true;
    AccountService.listAccounts('aws').then((loadedAccounts) => active && setAccounts(loadedAccounts));
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (stage.targetCluster) {
      const filterByCluster = AppListExtractor.monikerClusterNameFilter(stage.targetCluster);
      const moniker = AppListExtractor.getMonikers([application], filterByCluster)[0];
      if (moniker) {
        updateStageField({ stack: moniker.stack, freeFormDetails: moniker.detail });
      } else {
        const nameParts = NameUtils.parseClusterName(stage.targetCluster);
        updateStageField({ stack: nameParts.stack, freeFormDetails: nameParts.freeFormDetails });
      }
    } else if (stage.stack || stage.freeFormDetails) {
      updateStageField({ stack: '', freeFormDetails: '' });
    }
  }, [stage.targetCluster]);

  const toggleSuspendedProcess = (process: string) => {
    const suspendedProcesses: string[] = stage.suspendedProcesses ? [...stage.suspendedProcesses] : [];
    const index = suspendedProcesses.indexOf(process);
    if (index === -1) {
      suspendedProcesses.push(process);
    } else {
      suspendedProcesses.splice(index, 1);
    }
    updateStageField({ suspendedProcesses });
  };

  const blockDeviceMappingsSource = stage.copySourceCustomBlockDeviceMappings
    ? 'source'
    : stage.useAmiBlockDeviceMappings
    ? 'ami'
    : 'default';

  const selectBlockDeviceMappingsSource = (selection: string) => {
    if (selection === 'source') {
      updateStageField({ copySourceCustomBlockDeviceMappings: true, useAmiBlockDeviceMappings: false });
    } else if (selection === 'ami') {
      updateStageField({ copySourceCustomBlockDeviceMappings: false, useAmiBlockDeviceMappings: true });
    } else {
      updateStageField({ copySourceCustomBlockDeviceMappings: false, useAmiBlockDeviceMappings: false });
    }
  };

  return (
    <div className="form-horizontal">
      {!pipeline.strategy && (
        <AccountRegionClusterSelector
          accounts={accounts}
          application={application}
          clusterField="targetCluster"
          component={stage}
          onComponentUpdate={stageFieldUpdated}
          singleRegion="true"
        />
      )}
      <StageConfigField label="Target">
        <TargetSelect
          model={{ target: stage.target }}
          onChange={(target: string) => updateStageField({ target })}
          options={StageConstants.TARGET_LIST}
        />
      </StageConfigField>
      <StageConfigField label="Capacity">
        <div className="radio">
          <label>
            <input
              checked={stage.useSourceCapacity !== false}
              onChange={() => updateStageField({ useSourceCapacity: true, capacity: undefined })}
              type="radio"
            />
            Copy the capacity from the current server group <HelpField id="serverGroupCapacity.useSourceCapacityTrue" />
          </label>
        </div>
        <div className="radio">
          <label>
            <input
              checked={stage.useSourceCapacity === false}
              onChange={() => updateStageField({ useSourceCapacity: false })}
              type="radio"
            />
            Let me specify the capacity <HelpField id="serverGroupCapacity.useSourceCapacityFalse" />
          </label>
        </div>
      </StageConfigField>
      {stage.useSourceCapacity === false && (
        <>
          <div className="form-group">
            <div className="col-md-2 col-md-offset-3">Min</div>
            <div className="col-md-2">Max</div>
            <div className="col-md-2">Desired</div>
          </div>
          <div className="form-group">
            {capacityFields.map((field) => (
              <div className="col-md-2" style={field === 'min' ? { marginLeft: '25%' } : undefined} key={field}>
                <input
                  className="form-control input-sm"
                  onChange={(event) =>
                    updateStageField({
                      capacity: {
                        ...stage.capacity,
                        [field]: event.target.value === '' ? undefined : Number(event.target.value),
                      },
                    })
                  }
                  required
                  type="number"
                  value={stage.capacity?.[field] ?? ''}
                />
              </div>
            ))}
          </div>
        </>
      )}
      <StageConfigField label="Traffic" helpKey="aws.serverGroup.traffic">
        <div className="checkbox">
          <label>
            <input
              checked={!(stage.suspendedProcesses || []).includes('AddToLoadBalancer')}
              onChange={() => toggleSuspendedProcess('AddToLoadBalancer')}
              type="checkbox"
            />
            Send client requests to new instances
          </label>
        </div>
      </StageConfigField>
      <StageConfigField label="AMI Block Device Mappings">
        <div className="radio">
          <div>
            <label>
              <input
                checked={blockDeviceMappingsSource === 'source'}
                name="blockDeviceMappingsSource"
                onChange={() => selectBlockDeviceMappingsSource('source')}
                type="radio"
              />
              Copy from current server group <HelpField id="aws.blockDeviceMappings.useSource" />
            </label>
          </div>
          <div>
            <label>
              <input
                checked={blockDeviceMappingsSource === 'ami'}
                name="blockDeviceMappingsSource"
                onChange={() => selectBlockDeviceMappingsSource('ami')}
                type="radio"
              />
              Prefer AMI block device mappings <HelpField id="aws.blockDeviceMappings.useAMI" />
            </label>
          </div>
          <div>
            <label>
              <input
                checked={blockDeviceMappingsSource === 'default'}
                name="blockDeviceMappingsSource"
                onChange={() => selectBlockDeviceMappingsSource('default')}
                type="radio"
              />
              Defaults for selected instance type <HelpField id="aws.blockDeviceMappings.useDefaults" />
            </label>
          </div>
        </div>
      </StageConfigField>
      {application.attributes.platformHealthOnlyShowOverride && (
        <StageConfigField label="Task Completion">
          <PlatformHealthOverride
            interestingHealthProviderNames={stage.interestingHealthProviderNames || []}
            onChange={(interestingHealthProviderNames) => updateStageField({ interestingHealthProviderNames })}
            platformHealthType="Amazon"
          />
        </StageConfigField>
      )}
      <DeploymentStrategySelector
        command={stage as any}
        fieldColumns="6"
        onFieldChange={(key, value) => {
          set(stage, key, value);
          stageFieldUpdated();
        }}
        onStrategyChange={() => stageFieldUpdated()}
      />
    </div>
  );
}

export const awsCloneServerGroupStage = {
  key: 'cloneServerGroup',
  provides: 'cloneServerGroup',
  cloudProvider: 'aws',
  component: AwsCloneServerGroupStageConfig,
  accountExtractor: (stage: any) => [stage.context.credentials],
  validators: [
    { type: 'requiredField', fieldName: 'targetCluster', fieldLabel: 'cluster' },
    { type: 'requiredField', fieldName: 'target' },
    { type: 'requiredField', fieldName: 'region' },
    { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
  ],
};

export function registerAwsCloneServerGroupStage() {
  Registry.pipeline.registerStage(awsCloneServerGroupStage);
}

registerAwsCloneServerGroupStage();
