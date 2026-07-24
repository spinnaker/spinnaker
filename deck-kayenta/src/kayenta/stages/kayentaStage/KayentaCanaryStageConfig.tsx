import { CanaryScores } from 'kayenta/components/canaryScores';
import type { ICanaryConfig, IKayentaAccount, IKayentaStage } from 'kayenta/domain';
import { KayentaAccountType, KayentaAnalysisType } from 'kayenta/domain';
import { getCanaryConfigById, listKayentaAccounts } from 'kayenta/service/canaryConfig.service';
import { get, has, set, unset } from 'lodash';
import React, { useEffect, useState } from 'react';

import type { IAccountDetails, IStageConfigProps } from '@spinnaker/core';
import {
  AccountService,
  AccountTag,
  AngularServices,
  AppListExtractor,
  CloudProviderRegistry,
  logger,
  MapEditor,
  ProviderSelectionService,
  StageConfigField,
  useDeckRuntimeServices,
} from '@spinnaker/core';

import { AnalysisType } from './AnalysisType';
import { ForAnalysisType } from './ForAnalysisType';
import { KayentaStageConfigSection } from './KayentaStageConfigSection';
import type { IKayentaStageConfigModel } from './kayentaStageConfig.model';
import {
  addPair,
  createInitialKayentaStageConfigModel,
  editServerGroup,
  getLocationChoices,
  getRegion,
  getServerGroupName,
  handleAnalysisTypeChange,
  handleLegacySiteLocalRecipientsChange,
  initializeKayentaStage,
  onDelayBeforeCleanupChange,
  onLifetimeChange,
  populateScopeNameChoices,
  populateScopeWithExpressions,
  REAL_TIME_AUTOMATIC_PROVIDERS,
  setMetricStore,
} from './kayentaStageConfig.model';

import './kayentaStage.less';

export function KayentaCanaryStageConfig({ application, stage, updateStage }: IStageConfigProps) {
  const { serverGroupCommandBuilder, serverGroupTransformer } = useDeckRuntimeServices();
  const kayentaStage = (stage as unknown) as IKayentaStage;
  const [model, setModel] = useState<IKayentaStageConfigModel>(() => {
    const initialModel = createInitialKayentaStageConfigModel();
    initialModel.state.backingDataLoading = true;
    return initialModel;
  });
  const [, forceUpdate] = useState(0);
  const stageRefId = get(kayentaStage, 'refId');

  useEffect(() => {
    let active = true;
    const nextModel = createInitialKayentaStageConfigModel();
    nextModel.state.backingDataLoading = true;
    setModel(nextModel);

    loadBackingData(kayentaStage, application)
      .then((backingData) => {
        if (!active) {
          return;
        }
        initializeKayentaStage(kayentaStage, nextModel, backingData);
        updateStage({ ...kayentaStage });
      })
      .finally(() => {
        if (active) {
          nextModel.state.backingDataLoading = false;
          setModel({ ...nextModel });
        }
      });

    return () => {
      active = false;
    };
  }, [application, stageRefId]);

  const notifyStageChanged = () => {
    forceUpdate((version) => version + 1);
    updateStage({ ...kayentaStage });
  };

  const mutateStage = (mutator: () => void) => {
    mutator();
    notifyStageChanged();
  };

  if (model.state.backingDataLoading) {
    return <div className="text-center">Loading...</div>;
  }

  const scope = kayentaStage.canaryConfig?.scopes?.[0] || ({} as any);
  const metricsAccounts = model.kayentaAccounts.get(KayentaAccountType.MetricsStore) || [];
  const storageAccounts = model.kayentaAccounts.get(KayentaAccountType.ObjectStore) || [];
  const locationChoices = getLocationChoices(kayentaStage, model);
  const lookbackMins = kayentaStage.canaryConfig.lookbackMins;
  const lookbackMinsIsExpression = typeof lookbackMins === 'string' && lookbackMins.includes('${');

  return (
    <div className="form-horizontal canary-config-view">
      {model.state.lifetimeHoursUpdatedToDuration && (
        <div className="alert alert-warning">
          <p>
            <strong>Kayenta now supports analysis lifetimes shorter than 1 hour.</strong>
          </p>
          <p>
            This canary stage has been updated to reflect the new lifetime support. Click "Save Changes" to save the
            updated lifetime on the server.
          </p>
        </div>
      )}

      <KayentaStageConfigSection title="Analysis Config">
        <StageConfigField label="Analysis Type" helpKey="pipeline.config.canary.analysisType">
          <AnalysisType
            analysisTypes={model.state.analysisTypes}
            selectedType={kayentaStage.analysisType}
            onChange={(type) => mutateStage(() => handleAnalysisTypeChange(kayentaStage, model, type))}
          />
        </StageConfigField>

        <StageConfigField label="Config Name">
          <select
            required={true}
            className="form-control input-sm"
            value={kayentaStage.canaryConfig.canaryConfigId || ''}
            onChange={(event) => handleCanaryConfigSelect(event.target.value)}
          >
            <option value="" />
            {model.canaryConfigSummaries.map((summary) => (
              <option key={summary.id} value={summary.id}>
                {summary.name}
              </option>
            ))}
          </select>
        </StageConfigField>

        <ForAnalysisType stage={kayentaStage} types="realTime, realTimeAutomatic">
          <StageConfigField label="Lifetime" fieldColumns={8} helpKey="pipeline.config.canary.lifetime">
            <input
              type="number"
              min={0}
              value={model.state.lifetime.hours}
              className="form-control input-sm duration-input"
              onChange={(event) =>
                mutateStage(() => {
                  model.state.lifetime.hours = parseInt(event.target.value, 10) || 0;
                  onLifetimeChange(kayentaStage, model);
                })
              }
            />
            <span className="form-control-static"> hours </span>
            <input
              type="number"
              min={0}
              max={59}
              value={model.state.lifetime.minutes}
              className="form-control input-sm duration-input"
              onChange={(event) =>
                mutateStage(() => {
                  model.state.lifetime.minutes = parseInt(event.target.value, 10) || 0;
                  onLifetimeChange(kayentaStage, model);
                })
              }
            />
            <span className="form-control-static"> minutes </span>
          </StageConfigField>
        </ForAnalysisType>

        <ForAnalysisType stage={kayentaStage} types="retrospective">
          <StageConfigField label="Start Time" helpKey="pipeline.config.canary.startTimeIso">
            <input
              required={true}
              className="form-control input-sm"
              value={scope.startTimeIso || ''}
              type="text"
              onChange={(event) => mutateStage(() => (scope.startTimeIso = event.target.value))}
            />
          </StageConfigField>

          <StageConfigField label="End Time" helpKey="pipeline.config.canary.endTimeIso">
            <input
              required={true}
              className="form-control input-sm"
              value={scope.endTimeIso || ''}
              type="text"
              onChange={(event) => mutateStage(() => (scope.endTimeIso = event.target.value))}
            />
          </StageConfigField>
        </ForAnalysisType>

        <ForAnalysisType stage={kayentaStage} types="realTime, realTimeAutomatic">
          <StageConfigField label="Delay" helpKey="pipeline.config.canary.delayBeforeAnalysis" fieldColumns={5}>
            <input
              type="text"
              value={kayentaStage.canaryConfig.beginCanaryAnalysisAfterMins || ''}
              className="form-control input-sm small-inline-input"
              onChange={(event) =>
                mutateStage(() => (kayentaStage.canaryConfig.beginCanaryAnalysisAfterMins = event.target.value))
              }
            />
            <span className="form-control-static"> minutes before starting analysis </span>
          </StageConfigField>
        </ForAnalysisType>

        <StageConfigField label="Interval" helpKey="pipeline.config.canary.canaryInterval" fieldColumns={3}>
          <input
            type="text"
            value={kayentaStage.canaryConfig.canaryAnalysisIntervalMins || ''}
            className="form-control input-sm small-inline-input"
            onChange={(event) =>
              mutateStage(() => (kayentaStage.canaryConfig.canaryAnalysisIntervalMins = event.target.value))
            }
          />
          <span className="form-control-static"> minutes</span>
        </StageConfigField>

        <StageConfigField label="Step" fieldColumns={3}>
          <input
            className="form-control input-sm small-inline-input"
            value={scope.step || ''}
            type="number"
            min={0}
            onChange={(event) => mutateStage(() => (scope.step = parseInt(event.target.value, 10) || null))}
          />
          <span className="form-control-static"> seconds</span>
        </StageConfigField>

        <StageConfigField
          label="Baseline Offset"
          helpKey="pipeline.config.canary.baselineAnalysisOffset"
          fieldColumns={3}
        >
          <input
            className="form-control input-sm small-inline-input"
            value={(kayentaStage.canaryConfig as any).baselineAnalysisOffsetInMins || ''}
            type="text"
            min={0}
            onChange={(event) =>
              mutateStage(() => ((kayentaStage.canaryConfig as any).baselineAnalysisOffsetInMins = event.target.value))
            }
          />
          <span className="form-control-static"> minutes</span>
        </StageConfigField>

        <StageConfigField label="Lookback Type" helpKey="pipeline.config.canary.lookback" fieldColumns={3}>
          <select
            className="form-control input-sm"
            value={String(model.state.useLookback)}
            onChange={(event) =>
              mutateStage(() => {
                model.state.useLookback = event.target.value === 'true';
                if (!model.state.useLookback) {
                  delete kayentaStage.canaryConfig.lookbackMins;
                }
              })
            }
          >
            <option value="false">Growing</option>
            <option value="true">Sliding</option>
          </select>
        </StageConfigField>

        {model.state.useLookback && (
          <>
            <StageConfigField label="">
              {lookbackMinsIsExpression ? (
                <p className="form-control-static">
                  Using a sliding lookback duration defined by an expression viewable in the pipeline JSON editor.
                </p>
              ) : (
                <span>
                  <span className="form-control-static">with a look-back duration of </span>
                  <input
                    type="number"
                    min={1}
                    value={lookbackMins || ''}
                    className="form-control input-sm small-inline-input"
                    data-test-id="lookback-minutes-input"
                    max={model.state.lifetime.hours * 60}
                    required={true}
                    onChange={(event) =>
                      mutateStage(
                        () =>
                          ((kayentaStage.canaryConfig as any).lookbackMins = parseInt(event.target.value, 10) || null),
                      )
                    }
                  />
                  <span className="form-control-static"> minutes</span>
                </span>
              )}
            </StageConfigField>
            {!lookbackMinsIsExpression && Number(lookbackMins) > 0 && Number(lookbackMins) < 30 && (
              <div className="error-message col-md-12">
                <b>NOTE:</b> To provide enough data points for the canary analysis it is recommended to set the
                look-back time to at least 30 minutes.
              </div>
            )}
          </>
        )}
      </KayentaStageConfigSection>

      <ForAnalysisType stage={kayentaStage} types="realTimeAutomatic">
        <StageConfigField
          label="Delay Before Cleanup"
          fieldColumns={8}
          helpKey="pipeline.config.canary.delayBeforeCleanup"
        >
          <input
            type="number"
            min={0}
            value={model.state.delayBeforeCleanup.hours}
            className="form-control input-sm duration-input"
            onChange={(event) =>
              mutateStage(() => {
                model.state.delayBeforeCleanup.hours = parseInt(event.target.value, 10) || 0;
                onDelayBeforeCleanupChange(kayentaStage, model);
              })
            }
          />
          <span className="form-control-static"> hours </span>
          <input
            type="number"
            min={0}
            max={59}
            value={model.state.delayBeforeCleanup.minutes}
            className="form-control input-sm duration-input"
            onChange={(event) =>
              mutateStage(() => {
                model.state.delayBeforeCleanup.minutes = parseInt(event.target.value, 10) || 0;
                onDelayBeforeCleanupChange(kayentaStage, model);
              })
            }
          />
          <span className="form-control-static"> minutes </span>
        </StageConfigField>

        <KayentaStageConfigSection title="Baseline Version">
          {model.providers.length > 1 && kayentaStage.isNew && (
            <StageConfigField label="Provider">
              <select
                className="form-control input-sm"
                value={get(kayentaStage, 'deployments.baseline.cloudProvider') || ''}
                onChange={(event) => handleProviderChange(event.target.value)}
              >
                {model.providers.map((provider) => (
                  <option key={provider} value={provider}>
                    {provider}
                  </option>
                ))}
              </select>
            </StageConfigField>
          )}

          <StageConfigField label="Account">
            <select
              required={true}
              className="form-control input-sm"
              value={get(kayentaStage, 'deployments.baseline.account') || ''}
              onChange={(event) =>
                mutateStage(() => {
                  set(kayentaStage, 'deployments.baseline.account', event.target.value);
                  setClusterList(model);
                })
              }
            >
              <option value="" />
              {(model.accounts || []).map((account) => (
                <option key={account.name} value={account.name}>
                  {account.name}
                </option>
              ))}
            </select>
          </StageConfigField>

          <StageConfigField label="Cluster">
            <select
              required={true}
              className="form-control input-sm"
              value={get(kayentaStage, 'deployments.baseline.cluster') || ''}
              onChange={(event) =>
                mutateStage(() => set(kayentaStage, 'deployments.baseline.cluster', event.target.value))
              }
            >
              <option value="" />
              {model.clusterList.map((cluster) => (
                <option key={cluster} value={cluster}>
                  {cluster}
                </option>
              ))}
            </select>
          </StageConfigField>
        </KayentaStageConfigSection>
      </ForAnalysisType>

      <KayentaStageConfigSection
        title="Baseline + Canary Pair"
        header={
          kayentaStage.analysisType === KayentaAnalysisType.RealTime ? (
            <span
              title="Click to populate with expressions for resolving control and experiment scopes from an upstream clone stage."
              onClick={() => mutateStage(() => populateScopeWithExpressions(kayentaStage))}
              className="fa fa-magic clickable"
            />
          ) : null
        }
      >
        <ForAnalysisType stage={kayentaStage} types="realTime, retrospective">
          {renderScopeField('Baseline', scope.controlScope, (value) => (scope.controlScope = value))}
          {renderLocationField(
            'Baseline Location',
            'control',
            scope.controlLocation,
            (value) => (scope.controlLocation = value),
          )}
          {renderScopeField('Canary', scope.experimentScope, (value) => (scope.experimentScope = value))}
          {renderLocationField('Canary Location', 'experiment', scope.experimentLocation, (value) => {
            scope.experimentLocation = value;
          })}
        </ForAnalysisType>

        <ForAnalysisType stage={kayentaStage} types="realTimeAutomatic">
          <div className="row">
            <div className="well well-sm">
              <table className="table">
                <thead>
                  <tr>
                    <th>Location</th>
                    <th>Baseline</th>
                    <th>Canary</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {get(kayentaStage, 'deployments.serverGroupPairs', []).map((clusterPair: any, index: number) => (
                    <tr key={index}>
                      <td>
                        <AccountTag account={clusterPair.control.account} /> {getRegion(clusterPair.control)}
                      </td>
                      <td>
                        {getServerGroupName(clusterPair.control)}
                        <br />
                        <a onClick={() => handleEditServerGroup(clusterPair.control, index, 'control')}>Edit</a>
                      </td>
                      <td>
                        {getServerGroupName(clusterPair.experiment)}
                        <br />
                        <a onClick={() => handleEditServerGroup(clusterPair.experiment, index, 'experiment')}>Edit</a>
                      </td>
                      <td>
                        <a
                          onClick={() => mutateStage(() => kayentaStage.deployments.serverGroupPairs.splice(index, 1))}
                        >
                          <span className="glyphicon glyphicon-trash" />
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
                {!get(kayentaStage, 'deployments.serverGroupPairs', []).length && (
                  <tfoot>
                    <tr>
                      <td colSpan={4}>
                        <button className="btn btn-block btn-sm add-new" type="button" onClick={handleAddPair}>
                          <span className="glyphicon glyphicon-plus-sign" /> Add Baseline + Canary Pair
                        </button>
                      </td>
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
          </div>
        </ForAnalysisType>
      </KayentaStageConfigSection>

      <KayentaStageConfigSection title="Metric Scope">
        <ForAnalysisType stage={kayentaStage} types="realTimeAutomatic">
          {model.metricStore === 'atlas' && (
            <StageConfigField label="Dataset">
              <input
                type="checkbox"
                checked={model.state.useAtlasGlobalDataset}
                onChange={(event) =>
                  mutateStage(() => {
                    model.state.useAtlasGlobalDataset = event.target.checked;
                    kayentaStage.canaryConfig.scopes.forEach((s) => {
                      set(s, 'extendedScopeParams.dataset', event.target.checked ? 'global' : 'regional');
                      set(s, 'extendedScopeParams.type', 'asg');
                    });
                  })
                }
              />{' '}
              Use Global Atlas Dataset
            </StageConfigField>
          )}
        </ForAnalysisType>

        <ForAnalysisType stage={kayentaStage} types="realTime, retrospective">
          {model.metricStore === 'atlas' && (
            <StageConfigField label="Scope Type">
              <select
                required={true}
                className="form-control input-sm"
                value={model.state.atlasScopeType}
                onChange={(event) =>
                  mutateStage(() => {
                    model.state.atlasScopeType = event.target.value;
                    kayentaStage.canaryConfig.scopes.forEach((s) =>
                      set(s, 'extendedScopeParams.type', event.target.value),
                    );
                  })
                }
              >
                <option value="cluster">Cluster</option>
                <option value="query">Query</option>
              </select>
            </StageConfigField>
          )}
        </ForAnalysisType>

        <StageConfigField label="Extended Params" helpKey="pipeline.config.canary.extendedScopeParams">
          <MapEditor
            hiddenKeys={['resourceType', 'dataset', 'type', 'environment']}
            model={scope.extendedScopeParams || {}}
            onChange={(extendedScopeParams: any) =>
              mutateStage(() => {
                scope.extendedScopeParams = extendedScopeParams;
                if (model.metricStore === 'atlas') {
                  kayentaStage.canaryConfig.scopes.forEach((s) => {
                    set(
                      s,
                      'extendedScopeParams.type',
                      kayentaStage.analysisType === KayentaAnalysisType.RealTimeAutomatic
                        ? 'asg'
                        : model.state.atlasScopeType,
                    );
                    if (kayentaStage.analysisType === KayentaAnalysisType.RealTimeAutomatic) {
                      set(s, 'extendedScopeParams.dataset', model.state.useAtlasGlobalDataset ? 'global' : 'regional');
                      setAtlasEnvironment(s);
                    }
                  });
                }
              })
            }
          />
        </StageConfigField>
      </KayentaStageConfigSection>

      <KayentaStageConfigSection title="Scoring Thresholds">
        <CanaryScores
          onChange={({ unhealthyScore, successfulScore }) =>
            mutateStage(() => {
              kayentaStage.canaryConfig.scoreThresholds.pass = successfulScore;
              kayentaStage.canaryConfig.scoreThresholds.marginal = unhealthyScore;
            })
          }
          successfulHelpFieldId="pipeline.config.canary.passingScore"
          successfulLabel="Pass"
          successfulScore={kayentaStage.canaryConfig.scoreThresholds.pass}
          unhealthyHelpFieldId="pipeline.config.canary.marginalScore"
          unhealthyLabel="Marginal"
          unhealthyScore={kayentaStage.canaryConfig.scoreThresholds.marginal}
        />
      </KayentaStageConfigSection>

      {model.state.showAdvancedSettings && (
        <KayentaStageConfigSection title="Advanced Settings">
          {model.state.showLegacySiteLocalRecipients && (
            <StageConfigField label="Notification Emails" helpKey="pipeline.config.canary.legacySiteLocalRecipients">
              <textarea
                className="form-control input-sm"
                value={model.state.legacySiteLocalRecipients}
                onChange={(event) =>
                  mutateStage(() => {
                    model.state.legacySiteLocalRecipients = event.target.value;
                    handleLegacySiteLocalRecipientsChange(kayentaStage, model);
                  })
                }
                placeholder="Email addresses separated by commas"
              />
            </StageConfigField>
          )}

          {metricsAccounts.length > 1 && (
            <StageConfigField label="Metrics Account" helpKey="pipeline.config.metricsAccount">
              <select
                className="form-control input-sm"
                value={kayentaStage.canaryConfig.metricsAccountName || ''}
                onChange={(event) =>
                  mutateStage(() => (kayentaStage.canaryConfig.metricsAccountName = event.target.value))
                }
              >
                {metricsAccounts.map((account) => (
                  <option key={account.name} value={account.name}>
                    {account.name}
                  </option>
                ))}
              </select>
            </StageConfigField>
          )}

          {storageAccounts.length > 1 && (
            <StageConfigField label="Storage Account" helpKey="pipeline.config.storageAccount">
              <select
                className="form-control input-sm"
                value={kayentaStage.canaryConfig.storageAccountName || ''}
                onChange={(event) =>
                  mutateStage(() => (kayentaStage.canaryConfig.storageAccountName = event.target.value))
                }
              >
                {storageAccounts.map((account) => (
                  <option key={account.name} value={account.name}>
                    {account.name}
                  </option>
                ))}
              </select>
            </StageConfigField>
          )}

          {model.scopeNames.length > 1 && (
            <StageConfigField label="Scope Name">
              <select
                required={true}
                className="form-control input-sm"
                value={scope.scopeName || ''}
                onChange={(event) => mutateStage(() => (scope.scopeName = event.target.value))}
              >
                {model.scopeNames.map((scopeName) => (
                  <option key={scopeName} value={scopeName}>
                    {scopeName}
                  </option>
                ))}
              </select>
            </StageConfigField>
          )}

          {kayentaStage.canaryConfig.scopes.length > 1 && (
            <div className="alert alert-warning">
              <strong>Warning!</strong> This stage specifies more than one scope. Please edit the stage JSON to
              configure scopes beyond the first one.
            </div>
          )}
        </KayentaStageConfigSection>
      )}
    </div>
  );

  function handleCanaryConfigSelect(id: string) {
    const previousCanaryConfigId = kayentaStage.canaryConfig.canaryConfigId;
    mutateStage(() => {
      kayentaStage.canaryConfig.canaryConfigId = id;
    });
    getCanaryConfigById(id)
      .then((selectedCanaryConfigDetails) => {
        model.selectedCanaryConfigDetails = selectedCanaryConfigDetails;
        populateScopeNameChoices(kayentaStage, model);
        setMetricStore(kayentaStage, model);
        setModel({ ...model });
        notifyStageChanged();
      })
      .catch(() => {
        if (kayentaStage.canaryConfig.canaryConfigId === id) {
          mutateStage(() => {
            if (previousCanaryConfigId) {
              kayentaStage.canaryConfig.canaryConfigId = previousCanaryConfigId;
            } else {
              delete kayentaStage.canaryConfig.canaryConfigId;
            }
          });
        }
      });
  }

  function renderScopeField(label: string, value: string, setValue: (value: string) => void) {
    return (
      <StageConfigField
        label={label}
        helpKey={`pipeline.config.canary.${label === 'Baseline' ? 'baseline' : 'canary'}Group`}
      >
        {model.state.atlasScopeType === 'query' ? (
          <textarea
            className="form-control input-sm"
            required={true}
            value={value || ''}
            onChange={(event) => mutateStage(() => setValue(event.target.value))}
          />
        ) : (
          <input
            className="form-control input-sm"
            required={true}
            value={value || ''}
            type="text"
            onChange={(event) => mutateStage(() => setValue(event.target.value))}
          />
        )}
      </StageConfigField>
    );
  }

  function renderLocationField(
    label: string,
    type: 'control' | 'experiment',
    value: string,
    setValue: (value: string) => void,
  ) {
    const choices = locationChoices.combinedLocations[type];
    return (
      <StageConfigField
        label={label}
        helpKey={`pipeline.config.canary.${type === 'control' ? 'baseline' : 'canary'}Location`}
      >
        {locationChoices.hasChoices ? (
          <select
            required={true}
            className="form-control input-sm"
            value={value || ''}
            onChange={(event) => mutateStage(() => setValue(event.target.value))}
          >
            <option value="" />
            {choices.map((location) => (
              <option key={location} value={location}>
                {location}
              </option>
            ))}
          </select>
        ) : (
          <input
            className="form-control input-sm"
            value={value || ''}
            required={true}
            type="text"
            onChange={(event) => mutateStage(() => setValue(event.target.value))}
          />
        )}
        {locationChoices.hasChoices &&
          model.state.showAllLocations[type] &&
          locationChoices.recommendedLocations.length > 0 && (
            <button type="button" className="link pull-right" onClick={() => toggleAllLocations(type)}>
              Only show recommended locations
            </button>
          )}
        {locationChoices.hasChoices && !model.state.showAllLocations[type] && locationChoices.locations.length > 0 && (
          <button type="button" className="link pull-right" onClick={() => toggleAllLocations(type)}>
            Show all locations
          </button>
        )}
      </StageConfigField>
    );
  }

  function toggleAllLocations(type: 'control' | 'experiment') {
    mutateStage(() => {
      model.state.showAllLocations[type] = !model.state.showAllLocations[type];
      const choices = getLocationChoices(kayentaStage, model);
      kayentaStage.canaryConfig.scopes.forEach((s) => {
        if (!choices.combinedLocations.control.includes(s.controlLocation)) {
          delete s.controlLocation;
        }
        if (!choices.combinedLocations.experiment.includes(s.experimentLocation)) {
          delete s.experimentLocation;
        }
      });
    });
  }

  function handleProviderChange(provider: string) {
    const previousBaseline = {
      cloudProvider: get(kayentaStage, 'deployments.baseline.cloudProvider'),
      account: get(kayentaStage, 'deployments.baseline.account'),
      cluster: get(kayentaStage, 'deployments.baseline.cluster'),
    };
    const previousAccounts = model.accounts ? [...model.accounts] : model.accounts;
    const previousClusterList = [...model.clusterList];

    set(kayentaStage, 'deployments.baseline.cloudProvider', provider);
    set(kayentaStage, 'deployments.baseline.account', null);
    set(kayentaStage, 'deployments.baseline.cluster', null);
    AccountService.listAccounts(provider)
      .then((accounts) => {
        if (get(kayentaStage, 'deployments.baseline.cloudProvider') !== provider) {
          return;
        }
        model.accounts = accounts;
        setClusterList(model);
        notifyStageChanged();
      })
      .catch(() => {
        if (get(kayentaStage, 'deployments.baseline.cloudProvider') !== provider) {
          return;
        }
        restoreBaselineValue('cloudProvider', previousBaseline.cloudProvider);
        restoreBaselineValue('account', previousBaseline.account);
        restoreBaselineValue('cluster', previousBaseline.cluster);
        model.accounts = previousAccounts;
        model.clusterList = previousClusterList;
        notifyStageChanged();
      });
  }

  function restoreBaselineValue(field: 'cloudProvider' | 'account' | 'cluster', value: any) {
    const path = `deployments.baseline.${field}`;
    if (value === undefined) {
      unset(kayentaStage, path);
    } else {
      set(kayentaStage, path, value);
    }
  }

  function setClusterList(nextModel: IKayentaStageConfigModel) {
    nextModel.clusterList = AppListExtractor.getClusters([application], (serverGroup: any) =>
      has(kayentaStage, 'deployments.baseline.account')
        ? serverGroup.account === kayentaStage.deployments.baseline.account
        : true,
    );
    kayentaStage.canaryConfig.scopes.forEach(setAtlasEnvironment);
  }

  function setAtlasEnvironment(s: any) {
    if (model.metricStore === 'atlas' && kayentaStage.analysisType === KayentaAnalysisType.RealTimeAutomatic) {
      if (get(kayentaStage, 'deployments.baseline.account')) {
        const accountDetails = (model.accounts || []).find(
          ({ name }) => kayentaStage.deployments.baseline.account === name,
        );
        accountDetails && set(s, 'extendedScopeParams.environment', accountDetails.environment);
      } else {
        unset(s, 'extendedScopeParams.environment');
      }
    }
  }

  function modalDependencies() {
    return {
      application,
      cloudProviderRegistry: CloudProviderRegistry,
      providerSelectionService: ProviderSelectionService,
      serverGroupCommandBuilder,
      serverGroupTransformer,
      $uibModal: AngularServices.$uibModal,
    };
  }

  function handleAddPair() {
    addPair(kayentaStage, modalDependencies())
      .then(notifyStageChanged)
      .catch(() => undefined);
  }

  function handleEditServerGroup(serverGroup: any, index: number, type: 'control' | 'experiment') {
    editServerGroup(kayentaStage, modalDependencies(), serverGroup, index, type)
      .then(notifyStageChanged)
      .catch(() => undefined);
  }
}

async function loadBackingData(stage: IKayentaStage, application: any) {
  try {
    await application.ready();
    const selectedCanaryConfigDetails = await loadCanaryConfigDetails(stage);
    const kayentaAccounts = await loadKayentaAccounts();
    const providers = ((await AccountService.listProviders(application)) || []).filter((provider: string) =>
      REAL_TIME_AUTOMATIC_PROVIDERS.includes(provider),
    );
    const accountProvider =
      get(stage, 'deployments.baseline.cloudProvider') ||
      ((stage.isNew || !stage.analysisType || stage.analysisType === KayentaAnalysisType.RealTimeAutomatic) &&
      providers.length
        ? providers[0]
        : null);
    const accounts = accountProvider
      ? ((await AccountService.listAccounts(accountProvider)) as IAccountDetails[])
      : null;

    return {
      selectedCanaryConfigDetails,
      kayentaAccounts,
      providers,
      accounts,
      canaryConfigSummaries: application.getDataSource('canaryConfigs').data,
      applicationName: application.name,
      clusterList: AppListExtractor.getClusters([application], (serverGroup: any) =>
        has(stage, 'deployments.baseline.account') ? serverGroup.account === stage.deployments.baseline.account : true,
      ),
    };
  } catch (error) {
    logger.log({ level: 'WARN', action: 'Error loading backing data for Kayenta stage', error: error as Error });
    return { applicationName: application.name };
  }
}

async function loadCanaryConfigDetails(stage: IKayentaStage): Promise<ICanaryConfig> {
  if (!has(stage, 'canaryConfig.canaryConfigId')) {
    return null;
  }
  try {
    return await getCanaryConfigById(stage.canaryConfig.canaryConfigId);
  } catch (error) {
    logger.log({
      level: 'WARN',
      action: `Could not load canary config with id ${stage.canaryConfig.canaryConfigId}`,
      error: error as Error,
    });
    return null;
  }
}

async function loadKayentaAccounts(): Promise<Map<KayentaAccountType, IKayentaAccount[]>> {
  const mapped = new Map<KayentaAccountType, IKayentaAccount[]>();
  const accounts = await listKayentaAccounts();
  accounts.forEach((account) => {
    account.supportedTypes.forEach((type) => mapped.set(type, (mapped.get(type) || []).concat(account)));
  });
  return mapped;
}
