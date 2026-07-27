import { cloneDeep, isString, toInteger } from 'lodash';
import React from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import {
  AccountService,
  AppListExtractor,
  AuthenticationService,
  CloudProviderRegistry,
  HelpField,
  NameUtils,
  ProviderSelectionService,
  useDeckRuntimeServices,
} from '@spinnaker/core';

import { CanaryAnalysisNameSelector } from './CanaryAnalysisNameSelector';
import { CanaryScores } from './CanaryScores';

function isExpression(value: any) {
  return isString(value) && value.includes('${');
}

function parseNotificationHours(value: string) {
  return (value || '').split(',').map((item) => {
    const parsed = parseInt(item.trim(), 10);
    return !isNaN(parsed) ? parsed : 0;
  });
}

function getRegion(cluster: any) {
  if (cluster.region) {
    return cluster.region;
  }
  const availabilityZones = cluster.availabilityZones;
  if (availabilityZones) {
    const regions = Object.keys(availabilityZones);
    if (regions && regions.length) {
      return regions[0];
    }
  }
  return 'n/a';
}

function getClusterName(cluster: any) {
  return NameUtils.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
}

function cleanupClusterConfig(cluster: any, type: string) {
  delete cluster.credentials;
  if (cluster.freeFormDetails && cluster.freeFormDetails.split('-').pop() === type.toLowerCase()) {
    return;
  }
  if (cluster.freeFormDetails) {
    cluster.freeFormDetails += '-';
  }
  cluster.freeFormDetails += type.toLowerCase();
  cluster.moniker = NameUtils.getMoniker(cluster.application, cluster.stack, cluster.freeFormDetails);
}

function configureServerGroupCommandForEditing(command: any) {
  command.viewState.disableStrategySelection = true;
  command.viewState.hideClusterNamePreview = true;
  command.viewState.readOnlyFields = { credentials: true, region: true, subnet: true, useSourceCapacity: true };
  delete command.strategy;
}

function getDefaultCanaryConfig(pipeline: any) {
  return {
    name: [pipeline.name, 'Canary'].join(' - '),
    lifetimeHours: 3,
    canaryHealthCheckHandler: { minimumCanaryResultScore: 75 },
    canarySuccessCriteria: { canaryResultScore: 95 },
    actionsForUnhealthyCanary: [{ action: 'DISABLE' }, { action: 'TERMINATE', delayBeforeActionInMins: 60 }],
    canaryAnalysisConfig: {
      combinedCanaryResultStrategy: 'AGGREGATE',
      notificationHours: [1, 2, 3],
      canaryAnalysisIntervalMins: 30,
      useLookback: false,
      lookbackMins: 0,
      beginCanaryAnalysisAfterMins: 0,
    },
  };
}

export function initializeCanaryStage(stage: any, pipeline: any, user: any): { cc: any; cac: any } {
  stage.baseline = stage.baseline || {};
  stage.scaleUp = stage.scaleUp || { enabled: false };
  stage.canary = stage.canary || {};
  stage.canary.owner = stage.canary.owner || (user.authenticated ? user.name : null);
  stage.canary.watchers = stage.canary.watchers || [];

  const defaultCanaryConfig = getDefaultCanaryConfig(pipeline);
  stage.canary.canaryConfig = stage.canary.canaryConfig || defaultCanaryConfig;
  stage.canary.canaryConfig.canaryAnalysisConfig =
    stage.canary.canaryConfig.canaryAnalysisConfig || defaultCanaryConfig.canaryAnalysisConfig;
  stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours =
    stage.canary.canaryConfig.canaryAnalysisConfig.notificationHours ||
    defaultCanaryConfig.canaryAnalysisConfig.notificationHours;

  return {
    cc: stage.canary.canaryConfig,
    cac: stage.canary.canaryConfig.canaryAnalysisConfig,
  };
}

export function CanaryStageConfig(props: IStageConfigProps) {
  const runtimeServices = useDeckRuntimeServices();
  const { serverGroupCommandBuilder, serverGroupTransformer } = runtimeServices;
  const { application, pipeline, stage, stageFieldUpdated } = props;
  const user = AuthenticationService.getAuthenticatedUser();
  const { cc, cac } = initializeCanaryStage(stage, pipeline, user);
  cc.lifetimeHours = !isExpression(cc.lifetimeHours) ? toInteger(cc.lifetimeHours) || undefined : cc.lifetimeHours;
  cac.lookbackMins = !isExpression(cac.lookbackMins) ? toInteger(cac.lookbackMins) || undefined : cac.lookbackMins;
  cac.canaryAnalysisIntervalMins = !isExpression(cac.canaryAnalysisIntervalMins)
    ? toInteger(cac.canaryAnalysisIntervalMins) || undefined
    : cac.canaryAnalysisIntervalMins;
  stage.scaleUp.delay = !isExpression(stage.scaleUp.delay)
    ? toInteger(stage.scaleUp.delay) || undefined
    : stage.scaleUp.delay;
  stage.scaleUp.capacity = !isExpression(stage.scaleUp.capacity)
    ? toInteger(stage.scaleUp.capacity) || undefined
    : stage.scaleUp.capacity;
  const [accounts, setAccounts] = React.useState<any[]>([]);
  const [providers, setProviders] = React.useState<string[]>([]);
  const [overriddenCloudProvider, setOverriddenCloudProvider] = React.useState('aws');
  const [clusterList, setClusterList] = React.useState<any[]>([]);
  const [recipients, setRecipients] = React.useState(
    stage.canary.watchers
      ? Array.isArray(stage.canary.watchers)
        ? stage.canary.watchers.join(', ')
        : stage.canary.watchers
      : '',
  );
  const [notificationHours, setNotificationHours] = React.useState(cac.notificationHours.join(','));
  const [analysisType, setAnalysisType] = React.useState(cac.useLookback ? 'SLIDING_LOOKBACK' : 'GROWING');
  const [terminateUnhealthyCanaryEnabled, setTerminateUnhealthyCanaryEnabled] = React.useState(
    !!(cc.actionsForUnhealthyCanary || []).some((action: any) => action.action === 'TERMINATE'),
  );

  const getCloudProvider = () => stage.baseline.cloudProvider || overriddenCloudProvider || 'aws';

  const refreshClusters = () => {
    const filter = (cluster: any) => (stage.baseline.account ? cluster.account === stage.baseline.account : true);
    setClusterList(AppListExtractor.getClusters([application], filter));
  };

  React.useEffect(() => {
    AccountService.listProviders(application).then((result) => {
      if (result.length === 1) {
        setOverriddenCloudProvider(result[0]);
      } else if (!stage.cloudProviderType && stage.cloudProvider) {
        setOverriddenCloudProvider(stage.cloudProvider);
      } else {
        setProviders(result);
      }
    });
  }, [application, stage]);

  React.useEffect(() => {
    AccountService.listAccounts(getCloudProvider()).then((result) => setAccounts(result));
    application.serverGroups.ready().then(refreshClusters);
  }, [application, stage.baseline.account, stage.baseline.cloudProvider, overriddenCloudProvider]);

  const update = (updater: () => void) => {
    updater();
    stageFieldUpdated();
  };

  const updateWatchers = (value: string) => {
    if (value.includes('${')) {
      stage.canary.watchers = value;
    } else {
      stage.canary.watchers = value.split(',').map((email) => email.trim());
    }
  };

  const toggleTerminate = (enabled: boolean) => {
    setTerminateUnhealthyCanaryEnabled(enabled);
    cc.actionsForUnhealthyCanary = enabled
      ? [{ action: 'DISABLE' }, { action: 'TERMINATE', delayBeforeActionInMins: 60 }]
      : [{ action: 'DISABLE' }];
  };

  const terminateAction = (cc.actionsForUnhealthyCanary || []).find((action: any) => action.action === 'TERMINATE');

  const hasReactCloneServerGroupModal = (_application: any, _account: any, provider: any): boolean =>
    Boolean(provider?.serverGroup?.CloneServerGroupModal);

  const addClusterPair = () => {
    stage.clusterPairs = stage.clusterPairs || [];
    ProviderSelectionService.selectProvider(application, 'serverGroup', hasReactCloneServerGroupModal).then(
      (selectedProvider: string) => {
        const config = CloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
        const title = 'Add Cluster Pair';
        serverGroupCommandBuilder
          .buildNewServerGroupCommandForPipeline(selectedProvider, stage, pipeline)
          .then((command: any) => {
            configureServerGroupCommandForEditing(command);
            command.viewState.overrides = { capacity: { min: 1, max: 1, desired: 1 }, useSourceCapacity: false };
            command.viewState.disableNoTemplateSelection = true;
            command.viewState.customTemplateMessage =
              'Select a template to configure the canary and baseline cluster pair. If you want to configure the server groups differently, you can do so by clicking "Edit" after adding the pair.';
            if (!config.CloneServerGroupModal) {
              return Promise.reject(
                new Error(`No React clone server group modal is registered for provider "${selectedProvider}".`),
              );
            }
            return config.CloneServerGroupModal.show({ title, application, command }, runtimeServices);
          })
          .then((command: any) => {
            const baselineCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
            const canaryCluster = cloneDeep(baselineCluster);
            cleanupClusterConfig(baselineCluster, 'baseline');
            cleanupClusterConfig(canaryCluster, 'canary');
            stage.clusterPairs.push({ baseline: baselineCluster, canary: canaryCluster });
            stageFieldUpdated();
          })
          .catch(() => {});
      },
      () => {},
    );
  };

  const editCluster = (cluster: any, index: number, type: string) => {
    cluster.provider = cluster.provider || getCloudProvider() || 'aws';
    const config = CloudProviderRegistry.getValue(cluster.provider, 'serverGroup');
    const title = `Configure ${type} Cluster`;
    serverGroupCommandBuilder
      .buildServerGroupCommandFromPipeline(application, cluster, stage, pipeline)
      .then((command: any) => {
        configureServerGroupCommandForEditing(command);
        const detailsParts = command.freeFormDetails.split('-');
        const lastPart = detailsParts.pop();
        if (lastPart === type.toLowerCase()) {
          command.freeFormDetails = detailsParts.join('-');
        }
        return command;
      })
      .then((command: any) => {
        if (!config.CloneServerGroupModal) {
          return Promise.reject(
            new Error(`No React clone server group modal is registered for provider "${cluster.provider}".`),
          );
        }
        return config.CloneServerGroupModal.show({ title, application, command }, runtimeServices);
      })
      .then((command: any) => {
        const stageCluster = serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
        cleanupClusterConfig(stageCluster, type);
        stage.clusterPairs[index][type.toLowerCase()] = stageCluster;
        stageFieldUpdated();
      })
      .catch(() => {});
  };

  return (
    <>
      <div className="form-horizontal canary-config-view">
        <h4>Deployment</h4>
        <StageField label="Name">
          <input
            type="text"
            required={true}
            value={cc.name || ''}
            className="form-control input-sm"
            onChange={(e) => update(() => (cc.name = e.target.value))}
          />
        </StageField>
        <StageField label="Canary Lifetime">
          <input
            type="text"
            min="0"
            required={true}
            value={cc.lifetimeHours || ''}
            className="form-control input-sm"
            style={{ display: 'inline-block', width: '33%' }}
            onChange={(e) => update(() => (cc.lifetimeHours = e.target.value))}
          />{' '}
          <span className="form-control-static">hour(s)</span>
        </StageField>
        <div className="form-group">
          <div className="col-md-8 col-md-offset-3">
            <div className="checkbox">
              <label>
                <input
                  type="checkbox"
                  style={{ marginTop: 8 }}
                  checked={terminateUnhealthyCanaryEnabled}
                  onChange={(e) => update(() => toggleTerminate(e.target.checked))}
                />{' '}
                Terminate unhealthy canary after{' '}
                <input
                  type="number"
                  required={true}
                  min="1"
                  value={terminateAction ? terminateAction.delayBeforeActionInMins : 60}
                  disabled={!terminateUnhealthyCanaryEnabled}
                  className="form-control input-sm"
                  style={{ display: 'inline-block', margin: '0 5px', width: '15%' }}
                  onChange={(e) => update(() => (terminateAction.delayBeforeActionInMins = Number(e.target.value)))}
                />{' '}
                minutes <HelpField id="pipeline.config.canary.scaleUp" />
              </label>
            </div>
          </div>
        </div>
        <h5>
          Baseline Version <HelpField id="pipeline.config.canary.baselineVersion" />
        </h5>
        <div className="horizontal-rule" />
        {providers.length > 1 && stage.isNew && (
          <StageField label="Provider">
            <select
              className="form-control input-sm"
              value={stage.baseline.cloudProvider || ''}
              onChange={(e) =>
                update(() => {
                  stage.baseline.cloudProvider = e.target.value;
                  delete stage.baseline.cluster;
                  delete stage.baseline.account;
                  delete stage.clusterPairs;
                })
              }
            >
              <option value="" />
              {providers.map((provider) => (
                <option key={provider} value={provider}>
                  {provider}
                </option>
              ))}
            </select>
          </StageField>
        )}
        <StageField label="Account">
          <select
            required={true}
            className="form-control input-sm"
            value={stage.baseline.account || ''}
            onChange={(e) =>
              update(() => {
                stage.baseline.account = e.target.value;
                stage.baseline.cluster = undefined;
                refreshClusters();
              })
            }
          >
            <option value="" />
            {accounts.map((account) => (
              <option key={account.name || account} value={account.name || account}>
                {account.name || account}
              </option>
            ))}
          </select>
        </StageField>
        <StageField label="Cluster">
          <select
            required={true}
            className="form-control input-sm"
            value={stage.baseline.cluster || ''}
            onChange={(e) => update(() => (stage.baseline.cluster = e.target.value))}
          >
            <option value="" />
            {clusterList.map((cluster) => (
              <option key={cluster.name || cluster} value={cluster.name || cluster}>
                {cluster.name || cluster}
              </option>
            ))}
          </select>
        </StageField>
        <h5>
          Baseline / Canary Cluster Pairs <HelpField id="pipeline.config.canary.clusterPairs" />
        </h5>
        <div className="horizontal-rule" />
        <div className="row">
          <div style={{ margin: '10px 10px 0 50px' }}>
            <div className="well well-sm">
              <table className="table">
                <thead>
                  <tr>
                    <th>Location</th>
                    <th>Baseline Cluster</th>
                    <th>Canary Cluster</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {(stage.clusterPairs || []).map((clusterPair: any, index: number) => (
                    <tr key={index}>
                      <td>
                        {clusterPair.baseline.account} {getRegion(clusterPair.baseline)}
                      </td>
                      <td>
                        {getClusterName(clusterPair.baseline)}
                        <br />
                        <a onClick={() => editCluster(clusterPair.baseline, index, 'Baseline')}>Edit</a>
                      </td>
                      <td>
                        {getClusterName(clusterPair.canary)}
                        <br />
                        <a onClick={() => editCluster(clusterPair.canary, index, 'Canary')}>Edit</a>
                      </td>
                      <td>
                        <a onClick={() => update(() => stage.clusterPairs.splice(index, 1))}>
                          <span className="glyphicon glyphicon-trash" />
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={4}>
                      <button type="button" className="btn btn-block btn-sm add-new" onClick={addClusterPair}>
                        <span className="glyphicon glyphicon-plus-sign" /> Add cluster
                      </button>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
        </div>
      </div>
      <div className="form-horizontal canary-config-view">
        <h4>Analysis</h4>
        <StageField label="Configuration">
          <CanaryAnalysisNameSelector
            value={cac.name || ''}
            className="form-control input-sm"
            onChange={(value) => update(() => (cac.name = value))}
          />
        </StageField>
        <StageField label="Analysis Type">
          <select
            className="form-control input-sm"
            value={analysisType}
            onChange={(e) =>
              update(() => {
                setAnalysisType(e.target.value);
                cac.useLookback = e.target.value === 'SLIDING_LOOKBACK';
                if (e.target.value !== 'SLIDING_LOOKBACK') {
                  cac.lookbackMins = 0;
                }
              })
            }
          >
            <option value="GROWING">Growing</option>
            <option value="SLIDING_LOOKBACK">Sliding Lookback</option>
          </select>
        </StageField>
        {cac.useLookback && (
          <StageField label="Look-back Duration">
            {isExpression(cac.lookbackMins) ? (
              <p className="form-control-static">
                Using a sliding lookback duration defined by an expression viewable in the pipeline JSON editor.
              </p>
            ) : (
              <span>
                with a look-back duration of{' '}
                <input
                  type="number"
                  min="1"
                  max={cc.lifetimeHours * 60}
                  required={cac.useLookback}
                  value={cac.lookbackMins || ''}
                  className="form-control input-sm"
                  style={{ display: 'inline-block', margin: '0 5px', width: '15%' }}
                  onChange={(e) => update(() => (cac.lookbackMins = Number(e.target.value)))}
                />{' '}
                minutes
              </span>
            )}
          </StageField>
        )}
        {cac.useLookback && cac.lookbackMins > 0 && cac.lookbackMins < 30 && (
          <div className="error-message col-md-12">
            <b>NOTE:</b> To provide enough data points for the Canary Analysis it is recommended to set the look-back
            time to at least 30 minutes.
          </div>
        )}
        <StageField label="Warmup Period">
          <input
            type="text"
            required={true}
            value={cac.beginCanaryAnalysisAfterMins || ''}
            className="form-control input-sm"
            style={{ display: 'inline-block', width: '10%' }}
            onChange={(e) => update(() => (cac.beginCanaryAnalysisAfterMins = e.target.value))}
          />{' '}
          <span className="form-control-static"> minutes before starting analysis </span>
        </StageField>
        <StageField label="Notification Hours">
          <input
            type="text"
            value={notificationHours}
            className="form-control input-sm"
            onChange={(e) => {
              setNotificationHours(e.target.value);
              update(() => (cac.notificationHours = parseNotificationHours(e.target.value)));
            }}
          />
        </StageField>
        <StageField label="Report Frequency">
          {isExpression(cac.canaryAnalysisIntervalMins) ? (
            <p className="form-control-static">
              The report frequency is defined via an expression in the pipeline JSON editor.
            </p>
          ) : (
            <span>
              <input
                type="number"
                required={true}
                min="1"
                max={cc.lifetimeHours * 60}
                value={cac.canaryAnalysisIntervalMins || ''}
                className="form-control input-sm"
                style={{ width: '33%', display: 'inline-block' }}
                onChange={(e) => update(() => (cac.canaryAnalysisIntervalMins = Number(e.target.value)))}
              />{' '}
              <span className="form-control-static"> minutes</span>
            </span>
          )}
        </StageField>
        <StageField label="Report Email">
          <input
            type="text"
            required={true}
            value={stage.canary.owner || ''}
            className="form-control input-sm"
            onChange={(e) => update(() => (stage.canary.owner = e.target.value))}
          />
        </StageField>
        <StageField label="Notification Emails">
          <textarea
            value={recipients}
            className="form-control input-sm"
            onChange={(e) => {
              setRecipients(e.target.value);
              update(() => updateWatchers(e.target.value));
            }}
          />
        </StageField>
      </div>
      <div className="form-horizontal canary-config-view">
        <h4>Scoring</h4>
        {(stage.clusterPairs || []).length > 1 && (
          <StageField label="Result Strategy">
            <select
              className="form-control input-sm"
              value={cc.combinedCanaryResultStrategy || ''}
              onChange={(e) => update(() => (cc.combinedCanaryResultStrategy = e.target.value))}
            >
              <option value="LOWEST">lowest</option>
              <option value="AGGREGATE">average</option>
            </select>
          </StageField>
        )}
        <CanaryScores
          unhealthyScore={cc.canaryHealthCheckHandler.minimumCanaryResultScore}
          successfulScore={cc.canarySuccessCriteria.canaryResultScore}
          onChange={({ successfulScore, unhealthyScore }) =>
            update(() => {
              cc.canarySuccessCriteria.canaryResultScore = successfulScore;
              cc.canaryHealthCheckHandler.minimumCanaryResultScore = unhealthyScore;
            })
          }
        />
      </div>
      <div className="form-horizontal canary-config-view">
        <h4>Advanced</h4>
        <div className="form-group">
          <div className="col-md-11 col-md-offset-1">
            {isExpression(stage.scaleUp.delay) || isExpression(stage.scaleUp.capacity) ? (
              <div>
                This canary stage has a scale up delay or capacity defined via an expression in the pipeline JSON
                editor.
              </div>
            ) : (
              <div className="checkbox">
                <label>
                  <input
                    type="checkbox"
                    style={{ marginTop: 8 }}
                    checked={!!stage.scaleUp.enabled}
                    onChange={(e) => update(() => (stage.scaleUp.enabled = e.target.checked))}
                  />{' '}
                  After a <HelpField id="pipeline.config.canary.scaleUpDelay" label="delay" /> of{' '}
                  <input
                    type="number"
                    required={true}
                    min="0"
                    disabled={!stage.scaleUp.enabled}
                    value={stage.scaleUp.delay || ''}
                    className="form-control input-sm"
                    style={{ display: 'inline-block', margin: '0 5px', width: '8%' }}
                    onChange={(e) => update(() => (stage.scaleUp.delay = Number(e.target.value)))}
                  />{' '}
                  minutes, scale up the canary to a{' '}
                  <HelpField id="pipeline.config.canary.scaleUpCapacity" label="capacity" /> of{' '}
                  <input
                    type="number"
                    required={true}
                    min="1"
                    disabled={!stage.scaleUp.enabled}
                    value={stage.scaleUp.capacity || ''}
                    className="form-control input-sm"
                    style={{ display: 'inline-block', margin: '0 5px', width: '8%' }}
                    onChange={(e) => update(() => (stage.scaleUp.capacity = Number(e.target.value)))}
                  />{' '}
                  instances.
                </label>
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  );
}

function StageField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="form-group">
      <div className="col-md-2 col-md-offset-1 sm-label-right">
        <label>{label}</label>
      </div>
      <div className="col-md-9">{children}</div>
    </div>
  );
}
