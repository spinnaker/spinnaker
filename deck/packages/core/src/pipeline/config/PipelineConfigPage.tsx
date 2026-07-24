import { hri as HumanReadableIds } from 'human-readable-ids';
import { cloneDeep, defaultsDeep, get, isString, omit, pick } from 'lodash';
import React from 'react';

import { CreatePipeline } from './CreatePipeline';
import { AccountService } from '../../account/AccountService';
import type { IAccountDetails } from '../../account/AccountService';
import { PipelineConfigActions } from './actions/PipelineConfigActions';
import { DeletePipelineModal } from './actions/delete/DeletePipelineModal';
import { DisablePipelineModal } from './actions/disable/DisablePipelineModal';
import { EnablePipelineModal } from './actions/enable/EnablePipelineModal';
import { ShowPipelineHistoryModal } from './actions/history/ShowPipelineHistoryModal';
import { LockPipelineModal } from './actions/lock/LockPipelineModal';
import { EditPipelineJsonModal } from './actions/pipelineJson/EditPipelineJsonModal';
import { RenamePipelineModal } from './actions/rename/RenamePipelineModal';
import { ShowPipelineTemplateJsonModal } from './actions/templateJson/ShowPipelineTemplateJsonModal';
import { UnlockPipelineModal } from './actions/unlock/UnlockPipelineModal';
import type { Application } from '../../application';
import { ApplicationReader } from '../../application/service/ApplicationReader';
import { useDeckRuntimeServices } from '../../bootstrap/DeckRuntimeContext';
import { ViewStateCache } from '../../cache';
import { CloudProviderLogo } from '../../cloudProvider';
import { SETTINGS } from '../../config/settings';
import { CopyStageModal } from './copyStage/CopyStageModal';
import type { IExpectedArtifact, INotification, IPipeline, IStage, IStageTypeConfig } from '../../domain';
import { PipelineGraph } from './graph/PipelineGraph';
import type { IPipelineGraphNode } from './graph/pipelineGraph.service';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { NotificationsList } from '../../notification';
import { Markdown, PageNavigator, PageSection, ReactModal, ReactSelectInput } from '../../presentation';
import { Registry } from '../../registry';
import { ExecutionsTransformer } from '../service/ExecutionsTransformer';
import { PipelineConfigService } from './services/PipelineConfigService';
import { StageConfigWrapper } from './stages/StageConfigWrapper';
import { BaseProviderStageConfig } from './stages/baseProviderStage/BaseProviderStageConfig';
import { EditStageJsonModal } from './stages/common/EditStageJsonModal';
import { StageConfigField } from './stages/common/stageConfigField/StageConfigField';
import { ExecutionWindows } from './stages/executionWindows/ExecutionWindows';
import { OverrideFailure } from './stages/overrideFailure/OverrideFailure';
import { OverrideTimeout } from './stages/overrideTimeout/OverrideTimeout';
import { ProducesArtifacts } from './stages/producesArtifacts/ProducesArtifacts';
import { ConfigurePipelineTemplateModal } from './templates/ConfigurePipelineTemplateModal';
import { PipelineTemplateReader } from './templates/PipelineTemplateReader';
import { PipelineTemplateWriter } from './templates/PipelineTemplateWriter';
import { PipelineTemplateV2Service } from './templates/v2/pipelineTemplateV2.service';
import { Triggers } from './triggers/Triggers';
import { CopyToClipboard } from '../../utils';
import { PipelineConfigValidator } from './validation/PipelineConfigValidator';
import { Spinner } from '../../widgets';

export interface IPipelineConfigPageProps {
  app: Application;
  className?: string;
}

interface IPipelineConfigModel {
  pipeline: IPipeline;
  renderablePipeline: IPipeline;
  isTemplatedPipeline: boolean;
  isV2TemplatedPipeline: boolean;
  hasDynamicSource: boolean;
  templateError?: any;
  pipelineExecutions?: any[];
  currentExecution?: any;
}

interface IPipelineConfigViewState {
  section: string;
  stageIndex: number;
  loading?: boolean;
  revertCount: number;
  original?: string;
  originalRenderablePipeline?: string;
  isDirty?: boolean;
  saving?: boolean;
  saveError?: boolean;
  saveErrorMessage?: string;
}

interface ILoadState {
  loading: boolean;
  notFound: boolean;
  error?: any;
}

const warningMessage = 'You have unsaved changes.\nAre you sure you want to navigate away from this page?';
const configViewStateCache =
  ViewStateCache.get('pipelineConfig') || ViewStateCache.createCache('pipelineConfig', { version: 2 });

export const STAGE_IDENTITY_FIELDS: Array<keyof IStage> = ['requisiteStageRefIds', 'refId', 'isNew', 'name', 'type'];
export const COMMON_STAGE_FIELDS: Array<keyof IStage> = [
  ...STAGE_IDENTITY_FIELDS,
  'comments',
  'notifications',
  'sendNotifications',
  'failPipeline',
  'continuePipeline',
  'completeOtherBranchesThenFail',
  'failOnFailedExpressions',
  'stageEnabled',
  'restrictExecutionDuringTimeWindow',
  'restrictedExecutionWindow',
  'skipWindowText',
  'stageTimeoutMs',
  'expectedArtifacts',
];

function toJson(value: any): string {
  return JSON.stringify(value);
}

function fromJson<T>(value: string): T {
  return JSON.parse(value);
}

function buildViewStateCacheKey(applicationName: string, pipelineId: string): string {
  return `${applicationName}:${pipelineId}`;
}

function containsJinja(source: string): boolean {
  return !!source && (source.includes('{{') || source.includes('{%'));
}

function getErrorMessage(errorMsg: string): string {
  let msg = 'There was an error saving your pipeline';
  if (isString(errorMsg)) {
    msg += ': ' + errorMsg;
  }
  return `${msg}.`;
}

function populateExpectedArtifactDisplayNames(pipeline: IPipeline): void {
  (pipeline.expectedArtifacts || []).forEach((artifact: IExpectedArtifact) => {
    if (!artifact.displayName || artifact.displayName.length === 0) {
      artifact.displayName = artifact.matchArtifact?.name || HumanReadableIds.random();
    }
  });
}

function getInitialViewState(
  applicationName: string,
  pipeline: IPipeline,
  renderablePipeline: IPipeline,
): IPipelineConfigViewState {
  const cached = configViewStateCache.get(buildViewStateCacheKey(applicationName, pipeline.id)) || {};
  const stageIndex = Number(cached.stageIndex || 0);
  const hasStage = renderablePipeline.stages && renderablePipeline.stages.length > stageIndex;
  const section = cached.section === 'stage' && hasStage ? 'stage' : 'triggers';

  return {
    section,
    stageIndex: hasStage ? stageIndex : 0,
    loading: false,
    revertCount: 0,
    original: toJson(pipeline),
    originalRenderablePipeline: toJson(renderablePipeline),
    isDirty: false,
  };
}

function getLatestConfig(application: Application, pipeline: IPipeline): IPipeline {
  const dataSource = pipeline.strategy ? application.strategyConfigs : application.pipelineConfigs;
  return dataSource?.data?.find((config: IPipeline) => config.id === pipeline.id);
}

function stageTypeLabel(stage: IStage, config: IStageTypeConfig): string {
  return config?.label || stage.type || 'Stage';
}

export function applyStageConfigDefaults(stage: IStage, config: IStageTypeConfig): boolean {
  if (!stage || !config) {
    return false;
  }
  const before = toJson(stage);
  if (config.addAliasToConfig) {
    stage.alias = config.alias;
  }
  if (config.defaults) {
    defaultsDeep(stage, cloneDeep(config.defaults));
  }
  if (!stage.name && config.label) {
    stage.name = config.label;
  }
  return before !== toJson(stage);
}

function retainStageFields(stage: IStage, fields: Array<keyof IStage>, changes: Partial<IStage>): void {
  const retained = cloneDeep(pick(stage, fields));
  Object.keys(stage).forEach((key) => delete stage[key]);
  Object.assign(stage, retained, changes);
}

function buildStageRoleOptions(permissions: any): any[] {
  const roles = new Set<string>();
  ['READ', 'WRITE', 'EXECUTE'].forEach((permission) =>
    (permissions?.[permission] || []).forEach((role: string) => roles.add(role)),
  );

  return Array.from(roles).map((role, index) => ({
    available: true,
    id: index,
    label: role,
    name: role,
    roleId: role,
    value: role,
  }));
}

function useUnsavedChangeGuard(router: IRouterInjectedProps['router'], isDirty: boolean): void {
  React.useEffect(() => {
    const removeTransitionGuard = router.transitionService.onBefore({}, () => {
      if (isDirty && !window.confirm(warningMessage)) {
        return false;
      }
      return undefined;
    });

    window.onbeforeunload = () => {
      if (isDirty) {
        return warningMessage;
      }
      return undefined;
    };

    return () => {
      removeTransitionGuard();
      window.onbeforeunload = undefined;
    };
  }, [router, isDirty]);
}

async function refreshDataSource(dataSource: any, force?: boolean): Promise<void> {
  if (!dataSource) {
    return;
  }
  dataSource.activate?.();
  if (dataSource.refresh) {
    await Promise.resolve(dataSource.refresh(force));
  } else if (dataSource.ready) {
    await Promise.resolve(dataSource.ready());
  }
}

async function loadPipelineModel(application: Application, pipelineId: string, executionId?: string, isNew?: string) {
  await refreshDataSource(application.pipelineConfigs);
  await refreshDataSource(application.strategyConfigs);

  let selectedConfig = application.pipelineConfigs?.data?.find((config: IPipeline) => config.id === pipelineId);
  let pipelinePlan: IPipeline = null;
  let templateError: any = null;
  let isTemplatedPipeline = false;
  let isV2TemplatedPipeline = false;
  let hasDynamicSource = false;

  if (selectedConfig && selectedConfig.type === 'templatedPipeline') {
    selectedConfig = cloneDeep(selectedConfig);
    isTemplatedPipeline = true;
    isV2TemplatedPipeline = PipelineTemplateV2Service.isV2PipelineConfig(selectedConfig);
    hasDynamicSource = !isV2TemplatedPipeline && containsJinja(get(selectedConfig, 'config.pipeline.template.source'));

    if (isNew === '1') {
      selectedConfig.isNew = true;
    }

    if (!selectedConfig.isNew || isV2TemplatedPipeline) {
      try {
        pipelinePlan = await Promise.resolve(
          PipelineTemplateReader.getPipelinePlan(selectedConfig as any, executionId),
        );
      } catch (error) {
        templateError = error;
        selectedConfig.isNew = true;
      }
    }
  } else if (!selectedConfig) {
    selectedConfig = application.strategyConfigs?.data?.find((config: IPipeline) => config.id === pipelineId);
    if (selectedConfig) {
      selectedConfig = cloneDeep(selectedConfig);
    }
  } else {
    selectedConfig = cloneDeep(selectedConfig);
  }

  if (!selectedConfig) {
    return null;
  }

  selectedConfig.stages = selectedConfig.stages || [];
  selectedConfig.triggers = selectedConfig.triggers || [];
  selectedConfig.parameterConfig = selectedConfig.parameterConfig || [];
  selectedConfig.notifications = selectedConfig.notifications || [];
  populateExpectedArtifactDisplayNames(selectedConfig);

  const renderablePipeline = pipelinePlan || selectedConfig;
  renderablePipeline.stages = renderablePipeline.stages || [];
  renderablePipeline.triggers = renderablePipeline.triggers || [];
  renderablePipeline.parameterConfig = renderablePipeline.parameterConfig || [];
  renderablePipeline.notifications = renderablePipeline.notifications || [];

  return {
    pipeline: selectedConfig,
    renderablePipeline,
    isTemplatedPipeline,
    isV2TemplatedPipeline,
    hasDynamicSource,
    templateError,
  } as IPipelineConfigModel;
}

interface IPipelineStageConfigProps {
  accounts: IAccountDetails[] | null;
  accountLoadError: Error | null;
  application: Application;
  pipeline: IPipeline;
  stage: IStage;
  updateStageDependencies: (dependencies: string[]) => void;
  updateStageField: (changes: Partial<IStage>) => void;
  stageFieldUpdated: () => void;
  removeStage: (stage: IStage) => void;
}

function PipelineStageConfig({
  accounts,
  accountLoadError,
  application,
  pipeline,
  removeStage,
  stage,
  stageFieldUpdated,
  updateStageDependencies,
  updateStageField,
}: IPipelineStageConfigProps) {
  const [stageRoleOptions, setStageRoleOptions] = React.useState<any[]>([]);
  const lastAppliedDefaultsKey = React.useRef<string>();
  const canResolveStageTypes = accounts !== null && accounts.length > 0 && !accountLoadError;
  const configurableStageTypes = canResolveStageTypes
    ? Registry.pipeline
        .getConfigurableStageTypes(accounts)
        .filter((type) => !pipeline.strategy || type.strategy)
        .sort((a, b) => a.label.localeCompare(b.label))
    : [];
  const providerConfigs = stage.type ? Registry.pipeline.getProvidersFor(stage.type) : [];
  const baseStageType = providerConfigs[0]?.provides || stage.type;
  const selectedStageType = configurableStageTypes.find((type) => type.key === baseStageType);
  const baseStageConfig = Registry.pipeline
    .getStageTypes()
    .find((type) => type.key === baseStageType && type.useBaseProvider && !type.provides);
  const availableProviders = selectedStageType?.cloudProviders || [];
  const selectedProvider = stage.cloudProvider || stage.cloudProviderType;
  const providerFieldsIncoherent =
    !!selectedProvider && (stage.cloudProvider !== selectedProvider || stage.cloudProviderType !== selectedProvider);
  const inferredProvider =
    !stage.isNew && !selectedProvider && availableProviders.length === 1 ? availableProviders[0] : null;
  const inferredProviderConfig = inferredProvider
    ? providerConfigs.find(
        (providerConfig) =>
          providerConfig.cloudProvider === inferredProvider || providerConfig.providesFor?.includes(inferredProvider),
      )
    : null;
  const matchingProviderConfigs =
    canResolveStageTypes && selectedProvider && !providerFieldsIncoherent
      ? providerConfigs.filter(
          (providerConfig) =>
            providerConfig.cloudProvider === selectedProvider || providerConfig.providesFor?.includes(selectedProvider),
        )
      : [];
  const isBaseProviderStage = !!baseStageConfig && providerConfigs.length > 0;
  const providerConfig = matchingProviderConfigs[0];
  const config = isBaseProviderStage ? providerConfig : stage.type ? Registry.pipeline.getStageConfig(stage) : null;
  const displayConfig = config || baseStageConfig || selectedStageType;
  const label = stageTypeLabel(stage, displayConfig);
  const dependencyCandidates = stage ? PipelineConfigService.getDependencyCandidateStages(pipeline, stage) : [];
  const dependencyOptions = pipeline.stages.filter((candidate) => candidate !== stage);
  const availableDependencyIds = new Set(dependencyCandidates.map((candidate) => candidate.refId));
  const showProviders = configurableStageTypes.some((stageType) => (stageType.cloudProviders || []).length > 1);
  const stageTypeOptions = configurableStageTypes.map((stageType) => ({
    cloudProviders: stageType.cloudProviders || [],
    description: stageType.description,
    key: stageType.key,
    label: stageType.label,
    value: stageType.key,
  }));
  const stageDependencyOptions = dependencyOptions.map((candidate) => ({
    disabled:
      !availableDependencyIds.has(candidate.refId) && !(stage.requisiteStageRefIds || []).includes(candidate.refId),
    label: candidate.name || '[new stage]',
    name: candidate.name || '[new stage]',
    refId: candidate.refId,
    value: String(candidate.refId),
  }));

  React.useEffect(() => {
    if (!SETTINGS.feature.fiatEnabled || stage.type !== 'manualJudgment') {
      setStageRoleOptions([]);
      return undefined;
    }

    let cancelled = false;
    Promise.resolve(ApplicationReader.getApplicationPermissions(application.name)).then(
      (permissions) => {
        if (!cancelled) {
          setStageRoleOptions(buildStageRoleOptions(permissions));
        }
      },
      () => {
        if (!cancelled) {
          setStageRoleOptions([]);
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [application.name, stage.type]);

  React.useEffect(() => {
    if (!isBaseProviderStage || !selectedProvider || !providerFieldsIncoherent) {
      return;
    }
    stage.cloudProvider = selectedProvider;
    stage.cloudProviderType = selectedProvider;
    stageFieldUpdated();
  }, [isBaseProviderStage, providerFieldsIncoherent, selectedProvider, stage, stageFieldUpdated]);

  React.useEffect(() => {
    if (!inferredProvider || !inferredProviderConfig) {
      return;
    }
    stage.type = inferredProviderConfig.key || inferredProviderConfig.provides || stage.type;
    stage.cloudProvider = inferredProvider;
    stage.cloudProviderType = inferredProvider;
    stageFieldUpdated();
  }, [inferredProvider, inferredProviderConfig, stage, stageFieldUpdated]);

  const defaultsKey = `${stage.refId || ''}:${stage.type || ''}:${selectedProvider || ''}`;
  React.useEffect(() => {
    if (!config || providerFieldsIncoherent || lastAppliedDefaultsKey.current === defaultsKey) {
      return;
    }
    lastAppliedDefaultsKey.current = defaultsKey;
    if (applyStageConfigDefaults(stage, config)) {
      stageFieldUpdated();
    }
  }, [config, defaultsKey, providerFieldsIncoherent, stage, stageFieldUpdated]);

  const accountState = accountLoadError ? (
    <div className="alert alert-danger">Could not load application accounts: {accountLoadError.message}</div>
  ) : accounts === null ? (
    <div className="text-center small">
      <Spinner size="small" /> Loading application accounts...
    </div>
  ) : !accounts.length ? (
    <div className="alert alert-info">No application accounts are available.</div>
  ) : null;

  const updateReservedStageField = (changes: Partial<IStage>) => {
    Object.assign(stage, changes);
    stageFieldUpdated();
  };

  const selectStageType = (stageType: string) => {
    const selectedType = configurableStageTypes.find((type) => type.key === stageType);
    retainStageFields(stage, STAGE_IDENTITY_FIELDS, { type: stageType });
    if (selectedType?.label) {
      stage.name = stage.name || selectedType.label;
    }
    stageFieldUpdated();
  };

  const selectProvider = (provider: string) => {
    const selectedConfig = providerConfigs.find(
      (candidate) => candidate.cloudProvider === provider || candidate.providesFor?.includes(provider),
    );
    if (!selectedConfig) {
      throw new Error(`No provider implementation found for stage type "${baseStageType}" and provider "${provider}".`);
    }
    retainStageFields(stage, COMMON_STAGE_FIELDS, {
      type: selectedConfig.key || selectedConfig.provides || stage.type,
      cloudProvider: provider,
      cloudProviderType: provider,
    });
    stageFieldUpdated();
  };

  const renderStageTypeOption = (option: any) => (
    <div className="stage-choice">
      <div className="stage-choice-heading">
        <strong>{option.label}</strong>
        {showProviders && option.cloudProviders?.length > 0 && (
          <span className="available-providers">
            {option.cloudProviders.map((provider: string) => (
              <CloudProviderLogo key={provider} provider={provider} height="12px" width="12px" showTooltip={true} />
            ))}
          </span>
        )}
      </div>
      {option.description && <div>{option.description}</div>}
    </div>
  );

  const editStageJson = () => {
    ReactModal.show(EditStageJsonModal, { stage } as any, { dialogClassName: 'modal-lg modal-fullscreen' })
      .then(stageFieldUpdated)
      .catch(() => {});
  };

  const handleSendNotificationsChanged = (event: React.ChangeEvent<HTMLInputElement>) => {
    const sendNotifications = event.target.checked;
    updateStageField({ sendNotifications: sendNotifications || undefined });
  };

  const setOptionalStageEnabled = (enabled: boolean) => {
    if (enabled) {
      updateStageField({ stageEnabled: { type: 'expression', expression: stage.stageEnabled?.expression } });
    } else {
      delete stage.stageEnabled;
      stageFieldUpdated();
    }
  };

  const updateNotifications = (notifications: INotification[]) => updateStageField({ notifications });

  const renderStageDetails = () => {
    if (!stage.type) {
      return <p className="small">Select a stage type to configure this stage.</p>;
    }
    const stageConfig = config?.component ? (
      <StageConfigWrapper
        application={application}
        component={config.component}
        configuration={config.configuration}
        pipeline={pipeline}
        stage={stage}
        stageFieldUpdated={stageFieldUpdated}
        updateStageField={(changes: Partial<IStage>) => updateStageField(changes)}
        updateStage={(changes: Partial<IStage>) => updateStageField(changes)}
      />
    ) : null;

    if (isBaseProviderStage) {
      if (!canResolveStageTypes) {
        return <p className="small">Provider-specific configuration is unavailable until accounts are loaded.</p>;
      }
      if (inferredProvider) {
        return <p className="small">Inferring provider configuration...</p>;
      }
      if (providerFieldsIncoherent) {
        return <p className="small">Normalizing provider configuration...</p>;
      }
      return (
        <>
          <BaseProviderStageConfig
            providers={availableProviders}
            selectedProvider={selectedProvider}
            readOnly={!stage.isNew}
            onProviderChange={selectProvider}
          />
          {stageConfig}
          {selectedProvider && !stageConfig && (
            <div className="alert alert-warning">
              The {label} stage uses a legacy Angular-only editor. This direct React page can save, remove, reorder, and
              edit this stage as JSON, but its form editor still needs a React migration.
            </div>
          )}
        </>
      );
    }

    if (stageConfig) {
      return stageConfig;
    }

    return (
      <div className="alert alert-warning">
        The {label} stage uses a legacy Angular-only editor. This direct React page can save, remove, reorder, and edit
        this stage as JSON, but its form editor still needs a React migration.
      </div>
    );
  };

  const canConfigureNotifications = !pipeline.strategy && !displayConfig?.disableNotifications;

  return (
    <div className="form-horizontal">
      {accountState}
      <div className="row pipeline-stage-config-heading">
        <div className="col-md-3">
          <h4>{stage.name || '[new stage]'}</h4>
          {stage.type && label && (
            <p className="small">
              <strong>Stage type:</strong> {label}
              <br />
              {displayConfig?.description && <Markdown message={displayConfig.description} />}
            </p>
          )}
          {!stage.type && <p className="small">No stage type selected</p>}
        </div>
        <div className="col-md-7 form-horizontal">
          {canResolveStageTypes && (stage.isNew || !stage.type) && (
            <StageConfigField label="Type" labelColumns={2} fieldColumns={9}>
              <ReactSelectInput
                autoFocus={true}
                clearable={false}
                inputClassName="pipeline-stage-type-select"
                name="type"
                onChange={(event) => selectStageType(event.target.value)}
                optionRenderer={renderStageTypeOption}
                options={stageTypeOptions}
                placeholder="Select a stage type..."
                style={{ width: 250 }}
                value={stage.type || ''}
                valueRenderer={(option: any) => <strong>{option.label}</strong>}
              />
            </StageConfigField>
          )}
          <StageConfigField label="Stage Name" labelColumns={2} fieldColumns={9}>
            <input
              className="form-control input-sm"
              required={true}
              type="text"
              value={stage.name || ''}
              onChange={(event) => updateReservedStageField({ name: event.target.value })}
            />
          </StageConfigField>
          <StageConfigField label="Depends On" helpKey="pipeline.config.dependsOn" labelColumns={2} fieldColumns={9}>
            <ReactSelectInput
              inputClassName="pipeline-stage-dependency-select"
              multi={true}
              name="requisiteStageRefIds"
              onChange={(event) => updateStageDependencies(event.target.value || [])}
              options={stageDependencyOptions}
              value={(stage.requisiteStageRefIds || []).map(String)}
              valueRenderer={(option: any) => option.name}
            />
          </StageConfigField>
          {SETTINGS.feature.fiatEnabled && stage.type === 'manualJudgment' && (
            <StageConfigField
              label="Authorized Groups"
              helpKey="pipeline.config.trigger.authorizedUser"
              labelColumns={2}
              fieldColumns={9}
            >
              <ReactSelectInput
                inputClassName="pipeline-stage-roles-select"
                multi={true}
                name="selectedStageRoles"
                onChange={(event) => updateStageField({ selectedStageRoles: event.target.value || [] })}
                options={stageRoleOptions}
                value={(stage.selectedStageRoles || []).map(String)}
                valueRenderer={(option: any) => option.name}
              />
            </StageConfigField>
          )}
        </div>
        <div className="col-md-2 text-right">
          <button className="btn btn-sm btn-default" type="button" onClick={() => removeStage(stage)}>
            <span className="glyphicon glyphicon-trash" /> Remove stage
          </button>{' '}
          <button className="btn btn-sm btn-default" type="button" onClick={editStageJson}>
            <i className="fa fa-cog" /> Edit stage as JSON
          </button>
        </div>
      </div>
      <PageNavigator scrollableContainer=".pipeline-config-page" hideNavigation={true}>
        <PageSection pageKey="stage" label={`${label} Configuration`}>
          <div className="stage-details">{renderStageDetails()}</div>
        </PageSection>
        <PageSection pageKey="execution" label="Execution Options">
          <OverrideFailure
            failPipeline={stage.failPipeline}
            continuePipeline={stage.continuePipeline}
            completeOtherBranchesThenFail={stage.completeOtherBranchesThenFail}
            updateStageField={updateStageField}
          />
          {!pipeline.strategy && (
            <ExecutionWindows
              restrictExecutionDuringTimeWindow={stage.restrictExecutionDuringTimeWindow}
              restrictedExecutionWindow={stage.restrictedExecutionWindow}
              skipWindowText={stage.skipWindowText}
              updateStageField={updateStageField}
            />
          )}
          <OverrideTimeout
            stageConfig={displayConfig as any}
            stageTimeoutMs={stage.stageTimeoutMs}
            updateStageField={updateStageField}
          />
          <StageConfigField label="Fail on Failed Expressions" helpKey="pipeline.config.failOnFailedExpressions">
            <div className="checkbox">
              <label>
                <input
                  checked={!!stage.failOnFailedExpressions}
                  data-test-id="fail-on-failed-expressions"
                  onChange={(event) => updateStageField({ failOnFailedExpressions: event.target.checked })}
                  type="checkbox"
                />
              </label>
            </div>
          </StageConfigField>
          <StageConfigField label="Conditional on Expression" helpKey="pipeline.config.optionalStage">
            <div className="checkbox">
              <label>
                <input
                  checked={!!stage.stageEnabled}
                  data-test-id="optional-stage-enabled"
                  onChange={(event) => setOptionalStageEnabled(event.target.checked)}
                  type="checkbox"
                />
              </label>
            </div>
            {stage.stageEnabled && (
              <input
                className="form-control input-sm"
                data-test-id="optional-stage-expression"
                onChange={(event) =>
                  updateStageField({ stageEnabled: { type: 'expression', expression: event.target.value } })
                }
                type="text"
                value={stage.stageEnabled.expression || ''}
              />
            )}
          </StageConfigField>
        </PageSection>
        <PageSection
          pageKey="producesArtifacts"
          label="Produces Artifacts"
          visible={!!displayConfig?.producesArtifacts}
        >
          <ProducesArtifacts
            pipeline={pipeline}
            stage={stage}
            onProducesChanged={(artifacts) => updateStageField({ expectedArtifacts: artifacts })}
          />
        </PageSection>
        <PageSection pageKey="comments" label="Comments" noWrapper={true}>
          <textarea
            className="form-control"
            rows={3}
            placeholder="(Optional) anything that might be helpful to explain the purpose of this stage; HTML is okay"
            value={stage.comments || ''}
            onChange={(event) => updateStageField({ comments: event.target.value })}
          />
        </PageSection>
        <PageSection pageKey="notification" label="Notifications" visible={canConfigureNotifications}>
          <NotificationsList
            level="stage"
            handleSendNotificationsChanged={handleSendNotificationsChanged}
            notifications={stage.notifications || []}
            sendNotifications={!!stage.sendNotifications}
            stageType={stage.type}
            updateNotifications={updateNotifications}
          />
        </PageSection>
      </PageNavigator>
    </div>
  );
}

export function PipelineConfigPageComponent({
  app,
  className,
  router,
  stateParams: params,
  stateService,
}: IPipelineConfigPageProps & IRouterInjectedProps) {
  const { executionService } = useDeckRuntimeServices();
  const pipelineId = params.pipelineId as string;
  const executionId = params.executionId as string;
  const isNew = params.new as string;
  const [loadState, setLoadState] = React.useState<ILoadState>({ loading: true, notFound: false });
  const [model, setModel] = React.useState<IPipelineConfigModel>(null);
  const [viewState, setViewState] = React.useState<IPipelineConfigViewState>(null);
  const [preventSave, setPreventSave] = React.useState<boolean>(false);
  const [revision, setRevision] = React.useState<number>(0);
  const [accounts, setAccounts] = React.useState<IAccountDetails[] | null>(null);
  const [accountLoadError, setAccountLoadError] = React.useState<Error | null>(null);
  const mounted = React.useRef(true);
  const autoOpenedTemplateId = React.useRef<string>();
  const currentPipelineId = React.useRef(pipelineId);
  const pipelineLoadGeneration = React.useRef(0);
  const templateModalInvocation = React.useRef(0);
  currentPipelineId.current = pipelineId;

  React.useEffect(
    () => () => {
      mounted.current = false;
    },
    [],
  );

  const configureTemplate = (templateModel = model) => {
    if (!templateModel) {
      return;
    }
    const invocation = ++templateModalInvocation.current;
    const invokedPipelineId = templateModel.pipeline.id;
    const invokedLoadGeneration = pipelineLoadGeneration.current;
    const isCurrentInvocation = () =>
      mounted.current &&
      templateModalInvocation.current === invocation &&
      currentPipelineId.current === invokedPipelineId &&
      pipelineLoadGeneration.current === invokedLoadGeneration;
    setViewState((current) => ({ ...current, loading: true }));
    ReactModal.show(
      ConfigurePipelineTemplateModal,
      {
        application: app,
        executionId: (templateModel.renderablePipeline as any).executionId,
        isNew: templateModel.pipeline.isNew,
        pipelineId: templateModel.pipeline.id,
        pipelineTemplateConfig: cloneDeep(templateModel.pipeline),
      } as any,
      { dialogClassName: 'modal-lg' },
    )
      .then(({ plan, config }: { plan: IPipeline; config: IPipeline }) => {
        if (!isCurrentInvocation()) {
          return;
        }
        const nextConfig = cloneDeep(config);
        delete nextConfig.isNew;
        setModel((current) => {
          if (!isCurrentInvocation() || current?.pipeline?.id !== invokedPipelineId) {
            return current;
          }
          return {
            ...current,
            pipeline: nextConfig,
            renderablePipeline: { ...current.renderablePipeline, ...cloneDeep(plan) },
          };
        });
        setViewState((current) => (isCurrentInvocation() ? { ...current, isDirty: true, loading: false } : current));
      })
      .catch(() => {})
      .finally(() => {
        if (isCurrentInvocation()) {
          setViewState((current) => (isCurrentInvocation() ? { ...current, loading: false } : current));
        }
      });
  };

  React.useEffect(() => {
    let cancelled = false;
    setAccounts(null);
    setAccountLoadError(null);
    Promise.resolve(AccountService.applicationAccounts(app)).then(
      (loadedAccounts) => {
        if (!cancelled) {
          setAccounts(loadedAccounts);
        }
      },
      (error) => {
        if (!cancelled) {
          setAccountLoadError(error instanceof Error ? error : new Error(String(error)));
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [app]);

  React.useEffect(() => {
    pipelineLoadGeneration.current += 1;
    let cancelled = false;
    setLoadState({ loading: true, notFound: false });
    if (app.notFound || app.hasError) {
      setLoadState({ loading: false, notFound: true });
      return () => undefined;
    }

    loadPipelineModel(app, pipelineId, executionId, isNew).then(
      (loadedModel) => {
        if (cancelled) {
          return;
        }
        if (!loadedModel) {
          setModel(null);
          setViewState(null);
          setLoadState({ loading: false, notFound: true });
          return;
        }
        setModel(loadedModel);
        setViewState(getInitialViewState(app.name, loadedModel.pipeline, loadedModel.renderablePipeline));
        setLoadState({ loading: false, notFound: false });
        if (
          loadedModel.isTemplatedPipeline &&
          loadedModel.pipeline.isNew &&
          !loadedModel.hasDynamicSource &&
          autoOpenedTemplateId.current !== loadedModel.pipeline.id
        ) {
          autoOpenedTemplateId.current = loadedModel.pipeline.id;
          configureTemplate(loadedModel);
        }
      },
      (error) => {
        if (!cancelled) {
          setLoadState({ loading: false, notFound: false, error });
        }
      },
    );

    return () => {
      cancelled = true;
    };
  }, [app, pipelineId, executionId, isNew]);

  React.useEffect(() => {
    const subscription = PipelineConfigValidator.subscribe((validations) => setPreventSave(!!validations.preventSave));
    return () => subscription.unsubscribe();
  }, []);

  React.useEffect(() => {
    if (!model?.hasDynamicSource || !model.pipeline?.id) {
      return undefined;
    }
    let cancelled = false;
    executionService
      .getExecutionsForConfigIds([model.pipeline.id], {
        limit: 5,
        transform: true,
        application: app,
      })
      .then(
        (executions: any[]) => {
          if (cancelled) {
            return;
          }
          executions.forEach((execution) => ExecutionsTransformer.addBuildInfo(execution));
          const currentExecution =
            executions.find((execution) => execution.id === (model.renderablePipeline as any).executionId) ||
            executions.find((execution) => execution.id === executionId) ||
            executions[0];
          setModel((current) => ({ ...current, pipelineExecutions: executions, currentExecution }));
        },
        () => {
          if (!cancelled) {
            setModel((current) => ({ ...current, pipelineExecutions: [], currentExecution: null }));
          }
        },
      );
    return () => {
      cancelled = true;
    };
  }, [app, executionId, executionService, model?.hasDynamicSource, model?.pipeline?.id]);

  React.useEffect(() => {
    if (model && viewState) {
      configViewStateCache.put(buildViewStateCacheKey(app.name, model.pipeline.id), {
        section: viewState.section,
        stageIndex: viewState.stageIndex,
      });
    }
  }, [app.name, model?.pipeline?.id, viewState?.section, viewState?.stageIndex]);

  useUnsavedChangeGuard(router, !!viewState?.isDirty);

  const markDirty = (pipeline = model?.pipeline) => {
    if (!pipeline || !viewState?.original) {
      return;
    }
    setViewState((current) => ({ ...current, isDirty: current.original !== toJson(pipeline) }));
  };

  const setOriginal = (pipeline: IPipeline, renderablePipeline: IPipeline) => {
    setViewState((current) => ({
      ...current,
      original: toJson(pipeline),
      originalRenderablePipeline: toJson(renderablePipeline),
      isDirty: false,
    }));
  };

  const updateModel = (nextModel: IPipelineConfigModel, shouldMarkDirty = true) => {
    setModel(nextModel);
    if (shouldMarkDirty) {
      markDirty(nextModel.pipeline);
    }
  };

  const stageFieldUpdated = () => {
    setRevision((current) => current + 1);
    const nextRenderablePipeline = cloneDeep(model.renderablePipeline);
    const nextPipeline = model.isTemplatedPipeline ? model.pipeline : nextRenderablePipeline;
    updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline });
  };

  const updatePipelineConfig = (changes: Partial<IPipeline>) => {
    const nextPipeline = cloneDeep(model.pipeline);
    const nextChanges = model.isV2TemplatedPipeline
      ? (PipelineTemplateV2Service.filterInheritedConfig(cloneDeep(changes)) as Partial<IPipeline>)
      : changes;
    Object.assign(nextPipeline, nextChanges);

    let nextRenderablePipeline = model.renderablePipeline;
    if (model.isV2TemplatedPipeline) {
      nextRenderablePipeline = { ...model.renderablePipeline, ...changes };
    } else if (!model.isTemplatedPipeline) {
      nextRenderablePipeline = nextPipeline;
    }

    updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline });
  };

  const navigateToStage = (index: number) => {
    if (!model.renderablePipeline.stages || index < 0 || index >= model.renderablePipeline.stages.length) {
      setViewState((current) => ({ ...current, section: 'triggers' }));
      return;
    }
    setViewState((current) => ({ ...current, section: 'stage', stageIndex: index }));
  };

  const graphNodeClicked = (node: IPipelineGraphNode) => {
    if (node.section === 'stage') {
      navigateToStage(node.index);
    } else {
      setViewState((current) => ({ ...current, section: node.section || 'triggers' }));
    }
  };

  const addStage = (newStage: Partial<IStage> = { isNew: true }) => {
    const nextRenderablePipeline = cloneDeep(model.renderablePipeline);
    const stages = nextRenderablePipeline.stages || [];
    const stageToAdd = newStage as IStage;
    stageToAdd.refId = String(Math.max(0, ...stages.map((stage) => Number(stage.refId) || 0)) + 1);
    stageToAdd.requisiteStageRefIds = stageToAdd.requisiteStageRefIds || [];
    stageToAdd.name = stageToAdd.name || '';
    stageToAdd.type = stageToAdd.type || '';
    if (stages.length && viewState.section === 'stage') {
      stageToAdd.requisiteStageRefIds.push(stages[viewState.stageIndex].refId);
    }
    stages.push(stageToAdd);
    nextRenderablePipeline.stages = stages;
    const nextPipeline = model.isTemplatedPipeline ? model.pipeline : nextRenderablePipeline;
    updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline });
    setViewState((current) => ({ ...current, section: 'stage', stageIndex: stages.length - 1 }));
  };

  const copyExistingStage = () => {
    ReactModal.show(CopyStageModal, { application: app, forStrategyConfig: !!model.pipeline.strategy } as any)
      .then((stageTemplate: IStage) => addStage({ ...cloneDeep(stageTemplate), isNew: true }))
      .catch(() => {});
  };

  const removeStage = (stageToRemove: IStage) => {
    const nextRenderablePipeline = cloneDeep(model.renderablePipeline);
    const stageIndex = nextRenderablePipeline.stages.findIndex((stage) => stage.refId === stageToRemove.refId);
    if (stageIndex < 0) {
      return;
    }
    const removed = nextRenderablePipeline.stages[stageIndex];
    nextRenderablePipeline.stages.splice(stageIndex, 1);
    nextRenderablePipeline.stages.forEach((stage) => {
      if (removed.refId && stage.requisiteStageRefIds?.includes(removed.refId)) {
        stage.requisiteStageRefIds = stage.requisiteStageRefIds.filter((id) => id !== removed.refId);
        if (!stage.requisiteStageRefIds.length) {
          stage.requisiteStageRefIds = [...(removed.requisiteStageRefIds || [])];
        }
      }
    });
    const nextPipeline = model.isTemplatedPipeline ? model.pipeline : nextRenderablePipeline;
    updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline });
    const lastStageIndex = nextRenderablePipeline.stages.length - 1;
    setViewState((current) => ({
      ...current,
      section: lastStageIndex < 0 ? 'triggers' : current.section,
      stageIndex: Math.max(0, Math.min(current.stageIndex, lastStageIndex)),
    }));
  };

  const updateStageField = (stage: IStage, changes: Partial<IStage>) => {
    const before = toJson(stage);
    const allowedChanges = omit(changes, ['requisiteStageRefIds', 'refId', 'isNew', 'name', 'type']);
    Object.entries(allowedChanges).forEach(([key, value]) => {
      if (value !== undefined || key in stage) {
        stage[key] = value;
      }
    });
    if (before !== toJson(stage)) {
      stageFieldUpdated();
    }
  };

  const updateStageDependencies = (stageToUpdate: IStage, dependencies: string[]) => {
    const nextRenderablePipeline = cloneDeep(model.renderablePipeline);
    const nextStage = nextRenderablePipeline.stages.find((stage) => stage.refId === stageToUpdate.refId);
    if (!nextStage) {
      return;
    }
    nextStage.requisiteStageRefIds = dependencies;
    const nextPipeline = model.isTemplatedPipeline ? model.pipeline : nextRenderablePipeline;
    updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline });
  };

  const revertPipelineChanges = () => {
    const original = fromJson<IPipeline>(viewState.original);
    const originalRenderablePipeline = fromJson<IPipeline>(viewState.originalRenderablePipeline);
    const nextRenderablePipeline = model.isTemplatedPipeline ? originalRenderablePipeline : original;
    updateModel({ ...model, pipeline: original, renderablePipeline: nextRenderablePipeline }, false);
    setViewState((current) => {
      const lastStageIndex = nextRenderablePipeline.stages.length - 1;
      return {
        ...current,
        section: current.section === 'stage' && lastStageIndex < 0 ? 'triggers' : current.section,
        stageIndex: Math.max(0, Math.min(current.stageIndex, lastStageIndex)),
        revertCount: (current.revertCount || 0) + 1,
        isDirty: false,
      };
    });
  };

  const applyUpdateTs = (toSave: IPipeline) => {
    return Promise.resolve(refreshDataSource(app.pipelineConfigs, true)).then(() => {
      const latestFromServer = getLatestConfig(app, toSave);
      if (latestFromServer?.updateTs) {
        toSave.updateTs = latestFromServer.updateTs;
      }
      const nextRenderablePipeline = model.isTemplatedPipeline ? model.renderablePipeline : toSave;
      updateModel({ ...model, pipeline: toSave, renderablePipeline: nextRenderablePipeline }, false);
      setOriginal(toSave, nextRenderablePipeline);
    });
  };

  const savePipeline = (pipelineToSave?: IPipeline) => {
    setViewState((current) => ({ ...current, saving: true }));
    const toSave = cloneDeep(pipelineToSave || model.pipeline);
    Promise.resolve(PipelineConfigService.savePipeline(toSave)).then(
      () =>
        applyUpdateTs(toSave).then(() => {
          setViewState((current) => ({ ...current, saveError: false, saving: false, saveErrorMessage: null }));
        }),
      (err: any) =>
        setViewState((current) => ({
          ...current,
          saveError: true,
          saving: false,
          saveErrorMessage: getErrorMessage(get(err, 'data.message')),
        })),
    );
  };

  const renamePipeline = () => {
    ReactModal.show(RenamePipelineModal, { pipeline: model.pipeline, application: app } as any)
      .then((pipelineName: string) => {
        const nextPipeline = { ...model.pipeline, name: pipelineName };
        const nextRenderablePipeline = model.isTemplatedPipeline ? model.renderablePipeline : nextPipeline;
        updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline }, false);
        return applyUpdateTs(nextPipeline);
      })
      .catch(() => {});
  };

  const editPipelineJson = () => {
    ReactModal.show(
      EditPipelineJsonModal,
      { pipeline: model.pipeline, plan: model.isTemplatedPipeline ? model.renderablePipeline : null } as any,
      { dialogClassName: 'modal-lg modal-fullscreen' },
    )
      .then(() => {
        const nextPipeline = cloneDeep(model.pipeline);
        updateModel({
          ...model,
          pipeline: nextPipeline,
          renderablePipeline: model.isTemplatedPipeline ? model.renderablePipeline : nextPipeline,
        });
      })
      .catch(() => {});
  };

  const deletePipeline = () => {
    ReactModal.show(DeletePipelineModal, { pipeline: model.pipeline, application: app } as any).catch(() => {});
  };

  const disableToggled = (isDisabled: boolean) => {
    const nextPipeline = { ...model.pipeline, disabled: isDisabled };
    const nextRenderablePipeline = model.isTemplatedPipeline ? model.renderablePipeline : nextPipeline;
    updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline }, false);
    const original = fromJson<IPipeline>(viewState.original);
    original.disabled = isDisabled;
    setOriginal(original, nextRenderablePipeline);
  };

  const enablePipeline = () => {
    ReactModal.show(EnablePipelineModal, { pipeline: fromJson<IPipeline>(viewState.original) } as any)
      .then(() => disableToggled(false))
      .catch(() => {});
  };

  const disablePipeline = () => {
    ReactModal.show(DisablePipelineModal, { pipeline: fromJson<IPipeline>(viewState.original) } as any)
      .then(() => disableToggled(true))
      .catch(() => {});
  };

  const lockPipeline = () => {
    ReactModal.show(LockPipelineModal, { pipeline: model.pipeline } as any)
      .then((pipeline: IPipeline) => {
        const nextPipeline = { ...model.pipeline, locked: pipeline.locked };
        const nextRenderablePipeline = model.isTemplatedPipeline ? model.renderablePipeline : nextPipeline;
        updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline }, false);
        setOriginal(nextPipeline, nextRenderablePipeline);
      })
      .catch(() => {});
  };

  const unlockPipeline = () => {
    ReactModal.show(UnlockPipelineModal, { pipeline: model.pipeline } as any)
      .then(() => {
        const nextPipeline = cloneDeep(model.pipeline);
        delete nextPipeline.locked;
        const nextRenderablePipeline = model.isTemplatedPipeline ? model.renderablePipeline : nextPipeline;
        updateModel({ ...model, pipeline: nextPipeline, renderablePipeline: nextRenderablePipeline }, false);
        setOriginal(nextPipeline, nextRenderablePipeline);
      })
      .catch(() => {});
  };

  const showHistory = () => {
    ReactModal.show(ShowPipelineHistoryModal, {
      pipelineConfigId: model.pipeline.id,
      isStrategy: model.pipeline.strategy,
      currentConfig: viewState.isDirty ? cloneDeep(model.pipeline) : null,
    } as any)
      .then((newConfig: IPipeline) => {
        updateModel({ ...model, pipeline: newConfig, renderablePipeline: newConfig });
        savePipeline(newConfig);
      })
      .catch(() => {});
  };

  const saveTemplate = (template: any) => {
    return PipelineTemplateWriter.savePipelineTemplateV2(template).then((response) => {
      const id = response.variables.find((variable: any) => variable.key === 'pipelineTemplate.id').value;
      stateService.go('home.pipeline-templates.pipeline-templates-detail', {
        templateId: PipelineTemplateV2Service.idForTemplate({ id }),
      });
      return true;
    });
  };

  const exportPipelineTemplate = () => {
    const ownerEmail = get(app, 'attributes.email', '');
    const template = PipelineTemplateV2Service.createPipelineTemplate(model.pipeline, ownerEmail);
    ReactModal.show(ShowPipelineTemplateJsonModal, { template, saveTemplate } as any, {
      dialogClassName: 'modal-lg modal-fullscreen',
    }).catch(() => {});
  };

  if (loadState.notFound) {
    return <div className="text-center">No pipeline found with that name.</div>;
  }

  if (loadState.error) {
    return <div className="alert alert-danger">Could not load pipeline configuration.</div>;
  }

  if (loadState.loading || !model || !viewState) {
    return (
      <div className="text-center">
        <Spinner size="medium" /> Loading pipeline configuration...
      </div>
    );
  }

  const { pipeline, renderablePipeline } = model;
  const selectedStage = renderablePipeline.stages?.[viewState.stageIndex];
  const isValid = (pipeline.stages || []).every((stage) => stage.name && stage.type) && !preventSave;
  const permalink = window.location.href;
  const canConfigureTemplate = model.isTemplatedPipeline;

  return (
    <div
      className={['pipeline-config-page', 'container-fluid', 'full-width', className].filter(Boolean).join(' ')}
      style={{ paddingTop: 0 }}
    >
      <div className="row">
        <div className="col-md-10 col-md-offset-1" />
      </div>
      <div className="row">
        <div className="col-md-10 col-md-offset-1">
          <div className="pipeline-configurer" data-revision={revision}>
            <div className="pipeline-config-heading">
              <div className="config-heading-row">
                <h3>
                  <a className="btn btn-configure" onClick={() => stateService.go('^.executions')}>
                    <span className="glyphicon glyphicon glyphicon-circle-arrow-left" />
                  </a>{' '}
                  <a className="nav-popover">{pipeline.name}</a>
                  {pipeline.disabled && <span> (disabled)</span>}
                </h3>
                <div className="config-heading-actions">
                  <div className="permalink">
                    <a target="_blank" href={permalink}>
                      Permalink
                    </a>
                    <CopyToClipboard text={permalink} toolTip="Copy permalink to clipboard" />
                  </div>
                  <div className="create-action">
                    <CreatePipeline application={app} />
                  </div>
                  <div className="pipeline-actions text-right">
                    <PipelineConfigActions
                      pipeline={pipeline}
                      renamePipeline={renamePipeline}
                      deletePipeline={deletePipeline}
                      enablePipeline={enablePipeline}
                      disablePipeline={disablePipeline}
                      lockPipeline={lockPipeline}
                      unlockPipeline={unlockPipeline}
                      editPipelineJson={editPipelineJson}
                      showHistory={showHistory}
                      exportPipelineTemplate={exportPipelineTemplate}
                    />
                  </div>
                </div>
              </div>
              {pipeline.locked && (
                <div className="band band-info">
                  <span className="glyphicon glyphicon small glyphicon-lock" />{' '}
                  {pipeline.locked.description || 'This pipeline is locked and does not allow modification'}
                </div>
              )}
              {model.isTemplatedPipeline && !pipeline.locked && (
                <div className="band band-info">
                  <span className="glyphicon glyphicon small glyphicon-lock" /> Manual edits are not allowed on
                  templated pipelines
                </div>
              )}
              {model.hasDynamicSource && model.pipelineExecutions?.length > 0 && (
                <div className="band band-info">
                  <span className="glyphicon glyphicon small glyphicon-wrench" /> This template has a dynamic source.
                  The configuration is currently rendered using{' '}
                  {model.currentExecution?.name || model.currentExecution?.id}.
                </div>
              )}
              {model.pipelineExecutions && model.pipelineExecutions.length === 0 && (
                <div className="band band-warning">
                  <span className="glyphicon glyphicon small glyphicon-alert" /> This template has a dynamic source. The
                  configuration cannot be rendered before the pipeline has executed at least once.
                </div>
              )}
              {model.templateError && model.pipelineExecutions?.length > 0 && model.isTemplatedPipeline && (
                <div className="band band-warning">
                  <span className="glyphicon glyphicon small glyphicon-alert" /> Could not render the pipeline
                  configuration because of an error: {get(model.templateError, 'data.message')}{' '}
                  {get(model.templateError, 'data.error')}
                </div>
              )}
              <div className="config-heading-body">
                <div className="pipeline-graph-container pipeline-config-graph">
                  <PipelineGraph
                    viewState={viewState as any}
                    pipeline={renderablePipeline}
                    shouldValidate={true}
                    onNodeClick={graphNodeClicked}
                  />
                </div>
                <div className="row">
                  <div className="col-md-8">
                    <button className="btn btn-block btn-sm add-new" onClick={() => addStage()}>
                      <span className="glyphicon glyphicon-plus-sign" /> Add stage
                    </button>
                  </div>
                  <div className="col-md-4">
                    <button className="btn btn-block btn-sm add-new" onClick={copyExistingStage}>
                      <span className="glyphicon glyphicon-duplicate" /> Copy an existing stage
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <div className="pipeline-contents">
              <div className="pipeline-config-view">
                <div className="row horizontal">
                  <div className="col-md-12">
                    {viewState.section === 'triggers' && (
                      <Triggers
                        application={app}
                        pipeline={renderablePipeline}
                        fieldUpdated={stageFieldUpdated}
                        updatePipelineConfig={updatePipelineConfig}
                        revertCount={viewState.revertCount}
                      />
                    )}
                    {viewState.section === 'stage' && selectedStage && (
                      <PipelineStageConfig
                        accounts={accounts}
                        accountLoadError={accountLoadError}
                        application={app}
                        pipeline={renderablePipeline}
                        stage={selectedStage}
                        stageFieldUpdated={stageFieldUpdated}
                        updateStageDependencies={(dependencies) => updateStageDependencies(selectedStage, dependencies)}
                        updateStageField={(changes) => updateStageField(selectedStage, changes)}
                        removeStage={removeStage}
                      />
                    )}
                  </div>
                </div>
              </div>
              <div className="row main-footer fixed-footer horizontal">
                <div className="fixed-footer-button">
                  {!pipeline.locked && viewState.isDirty && !viewState.saving && (
                    <button
                      data-test-id="Pipeline.revertChanges"
                      className="btn btn-default"
                      onClick={revertPipelineChanges}
                    >
                      <span className="glyphicon glyphicon-flash" /> Revert
                    </button>
                  )}
                </div>
                <div className="text-right">
                  {viewState.saveError && (
                    <span className="alert alert-danger">
                      {viewState.saveErrorMessage}{' '}
                      <a
                        className="alert-dismiss"
                        onClick={(event) => {
                          event.preventDefault();
                          setViewState((current) => ({ ...current, saveError: false, saveErrorMessage: null }));
                        }}
                      >
                        [dismiss]
                      </a>
                    </span>
                  )}
                  {canConfigureTemplate && (
                    <button
                      className="btn btn-default"
                      data-test-id="configure-template"
                      disabled={viewState.loading}
                      onClick={() => configureTemplate()}
                    >
                      <span className="fa fa-wrench" /> Configure Template
                    </button>
                  )}{' '}
                  {!pipeline.locked && viewState.isDirty && (
                    <button className="btn btn-primary" disabled={!isValid} onClick={() => savePipeline()}>
                      {!viewState.saving && (
                        <span>
                          <span className="far fa-check-circle" /> Save Changes
                        </span>
                      )}
                      {viewState.saving && (
                        <span className="pulsing">
                          <span className="fa fa-cog fa-spin" /> Saving...
                        </span>
                      )}
                    </button>
                  )}
                  {!pipeline.locked && !viewState.isDirty && (
                    <span className="btn btn-link disabled">
                      <span className="far fa-check-circle" /> In sync with server
                    </span>
                  )}
                  {pipeline.locked && (
                    <span className="btn btn-link disabled">
                      <span className="glyphicon glyphicon-lock" /> Pipeline is locked
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export const PipelineConfigPage = withRouter(PipelineConfigPageComponent);
