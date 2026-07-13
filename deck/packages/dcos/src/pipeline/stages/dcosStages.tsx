import React from 'react';

import type { IExecutionDetailsSectionProps, IStageConfigProps } from '@spinnaker/core';
import { AccountService, ExecutionDetailsTasks, Registry, StageConfigField, StageConstants } from '@spinnaker/core';

import { DcosProviderSettings } from '../../dcos.settings';

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

function setDefault(stage: any, key: string, value: any) {
  if (stage[key] === undefined || stage[key] === null) {
    stage[key] = value;
  }
}

function setDeepDefault(target: any, path: string[], value: any) {
  const leaf = path[path.length - 1];
  const parent = path.slice(0, -1).reduce((current, key) => {
    current[key] = current[key] || {};
    return current[key];
  }, target);
  setDefault(parent, leaf, value);
}

function updateDeep(stage: any, path: string[], value: any) {
  const copy = { ...stage };
  const leaf = path[path.length - 1];
  const parent = path.slice(0, -1).reduce((current, key) => {
    current[key] = { ...(current[key] || {}) };
    return current[key];
  }, copy);
  parent[leaf] = value;
  return copy;
}

export function updateDcosLabels(stage: any, key: string, value: string) {
  return {
    ...stage,
    labels: {
      ...(stage.labels || {}),
      [key]: value,
    },
  };
}

export function addDcosKeyValueEntry(values: any) {
  const next = { ...(values || {}) };
  let index = 0;
  let key = 'key';
  while (Object.prototype.hasOwnProperty.call(next, key)) {
    index += 1;
    key = `key${index}`;
  }
  next[key] = '';
  return next;
}

function getDcosClusters(credentialsKeyedByAccount: any, account: string) {
  return credentialsKeyedByAccount?.[account]?.dcosClusters || [];
}

function chooseDcosAccount(credentialsKeyedByAccount: any, application: any) {
  const accounts = Object.keys(credentialsKeyedByAccount || {});
  const defaultAccount = DcosProviderSettings.defaults.account || application?.defaultCredentials?.dcos;
  return defaultAccount && accounts.includes(defaultAccount) ? defaultAccount : accounts[0] || defaultAccount;
}

function chooseDcosCluster(credentialsKeyedByAccount: any, account: string) {
  const clusters = getDcosClusters(credentialsKeyedByAccount, account).map((cluster: any) => cluster.name);
  const defaultCluster = DcosProviderSettings.defaults.dcosCluster;
  if (defaultCluster && clusters.includes(defaultCluster)) {
    return defaultCluster;
  }
  return clusters.length === 1 ? clusters[0] : undefined;
}

function setDockerRegistry(stage: any, credentialsKeyedByAccount: any) {
  const registry = credentialsKeyedByAccount?.[stage.account]?.dockerRegistries?.[0]?.accountName;
  if (registry) {
    setDeepDefault(stage, ['docker', 'image', 'registry'], registry);
  }
}

export function initializeDcosFindImageStage(stage: any, application: any) {
  setDefault(stage, 'cloudProvider', 'dcos');
  setDefault(stage, 'selectionStrategy', selectionStrategies[0].val);
  setDefault(stage, 'onlyEnabled', true);
  setDefault(stage, 'credentials', application?.defaultCredentials?.dcos);
}

export function initializeDcosRunJobStage(stage: any, application: any, credentialsKeyedByAccount: any = {}) {
  setDefault(stage, 'name', Date.now().toString());
  setDefault(stage, 'cloudProvider', 'dcos');
  setDefault(stage, 'application', application?.name);
  setDefault(stage, 'credentials', application?.defaultCredentials?.dcos);
  setDeepDefault(stage, ['docker', 'image'], {});
  setDefault(stage, 'account', chooseDcosAccount(credentialsKeyedByAccount, application) || stage.credentials);
  const cluster = chooseDcosCluster(credentialsKeyedByAccount, stage.account);
  if (cluster) {
    setDefault(stage, 'dcosCluster', cluster);
    setDefault(stage, 'region', cluster);
    setDefault(stage, 'cluster', cluster);
  }
  stage.general = { cpus: 0.01, gpus: 0.0, mem: 128, disk: 0, ...(stage.general || {}) };
  setDockerRegistry(stage, credentialsKeyedByAccount);
}

function KeyValueEditor({ label, values, onChange }: { label: string; values: any; onChange: (values: any) => void }) {
  const entries = Object.keys(values || {});
  const updateKey = (oldKey: string, newKey: string) => {
    if (oldKey === newKey) {
      return;
    }
    const next = { ...(values || {}) };
    if (Object.prototype.hasOwnProperty.call(next, newKey)) {
      return;
    }
    next[newKey] = next[oldKey];
    delete next[oldKey];
    onChange(next);
  };

  return (
    <StageConfigField label={label}>
      {entries.map((key) => (
        <div className="row" key={key}>
          <div className="col-md-5">
            <TextInput value={key} onChange={(value) => updateKey(key, value)} />
          </div>
          <div className="col-md-5">
            <TextInput value={values[key]} onChange={(value) => onChange({ ...(values || {}), [key]: value })} />
          </div>
          <div className="col-md-2">
            <button
              className="btn btn-sm btn-default"
              type="button"
              onClick={() => {
                const next = { ...(values || {}) };
                delete next[key];
                onChange(next);
              }}
            >
              Remove
            </button>
          </div>
        </div>
      ))}
      <button className="btn btn-sm btn-default" type="button" onClick={() => onChange(addDcosKeyValueEntry(values))}>
        Add {label}
      </button>
    </StageConfigField>
  );
}

function TextInput({ value, onChange, type = 'text' }: { value: any; onChange: (value: any) => void; type?: string }) {
  return (
    <input
      type={type}
      className="form-control input-sm"
      value={value || ''}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

function AccountAndClusterFields({ stage, updateStage, credentialsKeyedByAccount, accountField }: any) {
  const account = stage[accountField];
  const accounts = Object.keys(credentialsKeyedByAccount || {});
  const clusters = getDcosClusters(credentialsKeyedByAccount, account);

  const updateAccount = (newAccount: string) => {
    const nextStage = { ...stage, [accountField]: newAccount };
    if (accountField === 'account') {
      nextStage.credentials = newAccount;
    }
    const cluster = chooseDcosCluster(credentialsKeyedByAccount, newAccount);
    nextStage.dcosCluster = cluster;
    nextStage.region = cluster;
    nextStage.cluster = cluster;
    setDockerRegistry(nextStage, credentialsKeyedByAccount);
    updateStage(nextStage);
  };

  return (
    <>
      <StageConfigField label="Account">
        {accounts.length > 0 ? (
          <select
            className="form-control input-sm"
            value={account || ''}
            onChange={(e) => updateAccount(e.target.value)}
          >
            <option value="">Select account</option>
            {accounts.map((accountName) => (
              <option key={accountName} value={accountName}>
                {accountName}
              </option>
            ))}
          </select>
        ) : (
          <TextInput value={account} onChange={updateAccount} />
        )}
      </StageConfigField>
      <StageConfigField label="Cluster">
        {clusters.length > 0 ? (
          <select
            className="form-control input-sm"
            value={stage.dcosCluster || stage.region || stage.cluster || ''}
            onChange={(e) =>
              updateStage({ ...stage, dcosCluster: e.target.value, region: e.target.value, cluster: e.target.value })
            }
          >
            <option value="">Select cluster</option>
            {clusters.map((cluster: any) => (
              <option key={cluster.name} value={cluster.name}>
                {cluster.name}
              </option>
            ))}
          </select>
        ) : (
          <TextInput
            value={stage.dcosCluster || stage.region || stage.cluster}
            onChange={(value) => updateStage({ ...stage, dcosCluster: value, region: value, cluster: value })}
          />
        )}
      </StageConfigField>
    </>
  );
}

function useDcosCredentials(
  stage: any,
  updateStage: (stage: any) => void,
  initializeStage: (stage: any, credentials: any) => void,
) {
  const [credentialsKeyedByAccount, setCredentialsKeyedByAccount] = React.useState<any>({});
  const stageRef = React.useRef(stage);
  const updateStageRef = React.useRef(updateStage);
  const initializeStageRef = React.useRef(initializeStage);

  stageRef.current = stage;
  updateStageRef.current = updateStage;
  initializeStageRef.current = initializeStage;

  React.useEffect(() => {
    let cancelled = false;
    AccountService.getCredentialsKeyedByAccount('dcos').then((credentials) => {
      if (!cancelled) {
        setCredentialsKeyedByAccount(credentials);
        const nextStage = { ...(stageRef.current || {}) };
        initializeStageRef.current(nextStage, credentials);
        updateStageRef.current(nextStage);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  return credentialsKeyedByAccount;
}

export function DcosFindImageStageConfig({ application, stage, updateStage }: IStageConfigProps) {
  const credentialsKeyedByAccount = useDcosCredentials(stage, updateStage, (stageToInitialize) =>
    initializeDcosFindImageStage(stageToInitialize, application),
  );

  return (
    <div className="form-horizontal">
      <AccountAndClusterFields
        stage={{ ...stage, cluster: stage.cluster || stage.region || stage.dcosCluster }}
        updateStage={updateStage}
        credentialsKeyedByAccount={credentialsKeyedByAccount}
        accountField="credentials"
      />
      <StageConfigField label="Server Group Selection">
        <select
          className="form-control input-sm"
          value={stage.selectionStrategy || selectionStrategies[0].val}
          onChange={(e) => updateStage({ ...stage, selectionStrategy: e.target.value })}
        >
          {selectionStrategies.map((strategy) => (
            <option key={strategy.val} value={strategy.val} title={strategy.description}>
              {strategy.label}
            </option>
          ))}
        </select>
      </StageConfigField>
      <StageConfigField label="Server Group Filters">
        <label className="checkbox-inline">
          <input
            type="checkbox"
            checked={stage.onlyEnabled !== false}
            onChange={(e) => updateStage({ ...stage, onlyEnabled: e.target.checked })}
          />{' '}
          Only consider enabled Server Groups
        </label>
      </StageConfigField>
      <StageConfigField label="Image Name Pattern">
        <TextInput
          value={stage.imageNamePattern}
          onChange={(value) => updateStage({ ...stage, imageNamePattern: value })}
        />
      </StageConfigField>
    </div>
  );
}

export function DcosRunJobStageConfig({ application, stage, updateStage }: IStageConfigProps) {
  const credentialsKeyedByAccount = useDcosCredentials(stage, updateStage, (stageToInitialize, credentials) =>
    initializeDcosRunJobStage(stageToInitialize, application, credentials),
  );
  const dockerImage = stage.docker?.image || {};
  const updateDockerImage = (key: string, value: any) =>
    updateStage(updateDeep(stage, ['docker', 'image', key], value));
  const updateGeneral = (key: string, value: any) => updateStage(updateDeep(stage, ['general', key], value));

  return (
    <div className="form-horizontal">
      <AccountAndClusterFields
        stage={stage}
        updateStage={updateStage}
        credentialsKeyedByAccount={credentialsKeyedByAccount}
        accountField="account"
      />
      <StageConfigField label="Job ID">
        <TextInput value={stage.general?.id} onChange={(value) => updateGeneral('id', value)} />
      </StageConfigField>
      <StageConfigField label="Description">
        <TextInput value={stage.general?.description} onChange={(value) => updateGeneral('description', value)} />
      </StageConfigField>
      <StageConfigField label="Command">
        <TextInput value={stage.general?.cmd} onChange={(value) => updateGeneral('cmd', value)} />
      </StageConfigField>
      <StageConfigField label="Resources">
        <div className="row">
          <div className="col-md-3">
            <TextInput
              type="number"
              value={stage.general?.cpus}
              onChange={(value) => updateGeneral('cpus', Number(value))}
            />
          </div>
          <div className="col-md-3">
            <TextInput
              type="number"
              value={stage.general?.mem}
              onChange={(value) => updateGeneral('mem', Number(value))}
            />
          </div>
          <div className="col-md-3">
            <TextInput
              type="number"
              value={stage.general?.disk}
              onChange={(value) => updateGeneral('disk', Number(value))}
            />
          </div>
          <div className="col-md-3">
            <TextInput
              type="number"
              value={stage.general?.gpus}
              onChange={(value) => updateGeneral('gpus', Number(value))}
            />
          </div>
        </div>
        <div className="small text-muted">CPUs, memory, disk, GPUs</div>
      </StageConfigField>
      <StageConfigField label="Docker Registry">
        <TextInput value={dockerImage.registry} onChange={(value) => updateDockerImage('registry', value)} />
      </StageConfigField>
      <StageConfigField label="Docker Organization">
        <TextInput value={dockerImage.organization} onChange={(value) => updateDockerImage('organization', value)} />
      </StageConfigField>
      <StageConfigField label="Docker Image">
        <TextInput value={dockerImage.repository} onChange={(value) => updateDockerImage('repository', value)} />
      </StageConfigField>
      <StageConfigField label="Docker Tag">
        <TextInput value={dockerImage.tag} onChange={(value) => updateDockerImage('tag', value)} />
      </StageConfigField>
      <StageConfigField label="Property File">
        <TextInput value={stage.propertyFile} onChange={(value) => updateStage({ ...stage, propertyFile: value })} />
      </StageConfigField>
      <KeyValueEditor label="Labels" values={stage.labels} onChange={(labels) => updateStage({ ...stage, labels })} />
    </div>
  );
}

export function DcosStageConfig({ application, stage, updateStage }: IStageConfigProps) {
  React.useEffect(() => {
    const nextStage = { ...stage };
    setDefault(nextStage, 'cloudProvider', 'dcos');
    setDefault(nextStage, 'credentials', application?.defaultCredentials?.dcos);
    setDefault(nextStage, 'regions', application?.defaultRegions?.dcos ? [application.defaultRegions.dcos] : []);
    setDefault(nextStage, 'target', StageConstants.TARGET_LIST[0].val);
    if (nextStage.type === 'resizeServerGroup') {
      setDefault(nextStage, 'action', 'scale_up');
      setDefault(nextStage, 'resizeType', 'pct');
      setDefault(nextStage, 'capacity', {});
    }
    if (nextStage.type === 'runJob') {
      setDefault(nextStage, 'name', Date.now().toString());
      setDefault(nextStage, 'application', application?.name);
      setDefault(nextStage, 'docker', { image: {} });
    }
    updateStage(nextStage);
  }, []);

  return (
    <div className="form-horizontal">
      <StageConfigField label="Provider">
        <p className="form-control-static">DC/OS</p>
      </StageConfigField>
      <StageConfigField label="Account">
        <input
          className="form-control input-sm"
          value={stage.credentials || stage.account || ''}
          onChange={(event) => updateStage({ ...stage, credentials: event.target.value, account: event.target.value })}
        />
      </StageConfigField>
      <StageConfigField label="Region">
        <input
          className="form-control input-sm"
          value={(stage.regions || [stage.region || ''])[0] || ''}
          onChange={(event) => updateStage({ ...stage, regions: [event.target.value], region: event.target.value })}
        />
      </StageConfigField>
      <StageConfigField label="Cluster">
        <input
          className="form-control input-sm"
          value={stage.cluster || ''}
          onChange={(event) => updateStage({ ...stage, cluster: event.target.value })}
        />
      </StageConfigField>
    </div>
  );
}

function DcosExecutionDetails({ stage }: IExecutionDetailsSectionProps) {
  return (
    <div className="row">
      <div className="col-md-12">
        <dl className="dl-horizontal dl-narrow">
          <dt>Account</dt>
          <dd>{stage.context.credentials || stage.context.account || '-'}</dd>
          <dt>Region</dt>
          <dd>{stage.context.region || stage.context.regions?.join(', ') || '-'}</dd>
          <dt>Cluster</dt>
          <dd>{stage.context.cluster || '-'}</dd>
        </dl>
      </div>
    </div>
  );
}
DcosExecutionDetails.title = 'dcosConfig';

const commonRequired = [
  { type: 'requiredField', fieldName: 'cluster' },
  { type: 'requiredField', fieldName: 'regions' },
  { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
];

function registerDcosStage(config: any) {
  const stageConfig = {
    cloudProvider: 'dcos',
    component: DcosStageConfig,
    executionDetailsSections: [DcosExecutionDetails, ExecutionDetailsTasks],
    ...config,
  };

  if (Registry.pipeline.getStageTypes().some((stage) => stage.key === stageConfig.key)) {
    return;
  }

  Registry.pipeline.registerStage(stageConfig);
}

export function registerDcosPipelineStages() {
  registerDcosStage({
    key: 'dcosDestroyServerGroup',
    provides: 'destroyServerGroup',
    alias: 'destroyAsg',
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to destroy a server group without deploying a new version into the same cluster.',
      },
      ...commonRequired,
      { type: 'requiredField', fieldName: 'target' },
    ],
  });

  registerDcosStage({
    key: 'dcosDisableServerGroup',
    provides: 'disableServerGroup',
    alias: 'disableAsg',
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to disable a server group without deploying a new version into the same cluster.',
      },
      ...commonRequired,
      { type: 'requiredField', fieldName: 'target' },
    ],
  });

  registerDcosStage({
    key: 'dcosDisableCluster',
    provides: 'disableCluster',
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });

  registerDcosStage({
    key: 'dcosFindImage',
    provides: 'findImage',
    component: DcosFindImageStageConfig,
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'selectionStrategy', fieldLabel: 'Server Group Selection' },
      { type: 'requiredField', fieldName: 'credentials' },
    ],
  });

  registerDcosStage({
    key: 'dcosResizeServerGroup',
    provides: 'resizeServerGroup',
    validators: [
      {
        type: 'targetImpedance',
        message:
          'This pipeline will attempt to resize a server group without deploying a new version into the same cluster.',
      },
      ...commonRequired,
      { type: 'requiredField', fieldName: 'target' },
      { type: 'requiredField', fieldName: 'action' },
    ],
  });

  registerDcosStage({
    key: 'dcosRunJob',
    provides: 'runJob',
    component: DcosRunJobStageConfig,
    validators: [
      { type: 'requiredField', fieldName: 'account' },
      { type: 'requiredField', fieldName: 'general.id' },
    ],
  });

  registerDcosStage({
    key: 'dcosScaleDownCluster',
    provides: 'scaleDownCluster',
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });

  registerDcosStage({
    key: 'dcosShrinkCluster',
    provides: 'shrinkCluster',
    validators: [
      { type: 'requiredField', fieldName: 'cluster' },
      { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
    ],
  });
}

registerDcosPipelineStages();
