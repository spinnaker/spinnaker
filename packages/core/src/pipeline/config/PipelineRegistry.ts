import { cloneDeep, fromPairs, intersection, isNil, memoize, uniq } from 'lodash';
import { ComponentType, SFC } from 'react';

import { IAccountDetails } from '../../account/AccountService';
import { Application } from '../../application/application.model';
import { CloudProviderRegistry, ICloudProviderConfig } from '../../cloudProvider';
import { SETTINGS } from '../../config/settings';
import {
  IArtifactEditorProps,
  IArtifactKindConfig,
  IExecution,
  INotificationTypeConfig,
  IStage,
  IStageOrTriggerTypeConfig,
  IStageTypeConfig,
  ITriggerTypeConfig,
} from '../../domain';

import { ITriggerTemplateComponentProps } from '../manualExecution/TriggerTemplate';
import { PreconfiguredJobReader } from './stages/preconfiguredJob';
import { artifactKindConfigs } from './triggers/artifacts';

export interface ITransformer {
  transform: (application: Application, execution: IExecution) => void;
}

export class PipelineRegistry {
  private triggerTypes: ITriggerTypeConfig[] = [];
  private stageTypes: IStageTypeConfig[] = [];
  private transformers: ITransformer[] = [];
  private notificationTypes: INotificationTypeConfig[] = [];
  private artifactKinds: IArtifactKindConfig[] = artifactKindConfigs;

  constructor() {
    this.getStageConfig = memoize(this.getStageConfig.bind(this), (stage: IStage) =>
      [stage ? stage.type : '', stage ? PipelineRegistry.resolveCloudProvider(stage) : ''].join(':'),
    );
  }

  private normalizeStageTypes(): void {
    this.stageTypes
      .filter((stageType) => {
        return stageType.provides;
      })
      .forEach((stageType) => {
        const parent = this.stageTypes.find((parentType) => {
          return parentType.key === stageType.provides && !parentType.provides;
        });
        if (parent) {
          stageType.label = stageType.label || parent.label;
          stageType.description = stageType.description || parent.description;
          stageType.key = stageType.key || parent.key;
          stageType.manualExecutionComponent = stageType.manualExecutionComponent || parent.manualExecutionComponent;

          // Optional parameters
          if (parent.executionDetailsUrl && !stageType.executionDetailsUrl) {
            stageType.executionDetailsUrl = parent.executionDetailsUrl;
          }
          if (parent.executionConfigSections && !stageType.executionConfigSections) {
            stageType.executionConfigSections = parent.executionConfigSections;
          }
          if (parent.executionDetailsSections && !stageType.executionDetailsSections) {
            stageType.executionDetailsSections = parent.executionDetailsSections;
          }
        }
      });
  }

  public registerNotification(notificationConfig: INotificationTypeConfig): void {
    this.notificationTypes.push(notificationConfig);
  }

  public registerTrigger(triggerConfig: ITriggerTypeConfig): void {
    if (SETTINGS.triggerTypes) {
      if (SETTINGS.triggerTypes.indexOf(triggerConfig.key) >= 0) {
        this.triggerTypes.push(triggerConfig);
      }
    } else {
      this.triggerTypes.push(triggerConfig);
    }
  }

  public registerTransformer(transformer: ITransformer): void {
    this.transformers.push(transformer);
  }

  public registerStage(stageConfig: IStageTypeConfig): void {
    if ((SETTINGS.hiddenStages || []).includes(stageConfig.key)) {
      return;
    }
    this.stageTypes.push(stageConfig);
    this.normalizeStageTypes();
  }

  /**
   * Registers a custom UI for a preconfigured run job stage.
   *
   * Fetches and applies the preconfigured job configuration from Gate.
   * The following IStageTypeConfig fields are overwritten:
   *
   * - configuration.parameters
   * - configuration.waitForCompletion
   * - defaults
   * - description
   * - label
   * - producesArtifacts
   *
   * @param stageConfigSkeleton a partial IStageTypeConfig (typically from makePreconfiguredJobStage())
   * @returns a promise for the IStageTypeConfig that got registered
   */
  public async registerPreconfiguredJobStage(stageConfigSkeleton: IStageTypeConfig): Promise<IStageTypeConfig> {
    const preconfiguredJobsFromGate = await PreconfiguredJobReader.list();
    const job = preconfiguredJobsFromGate.find((j) => j.type === stageConfigSkeleton.key);

    if (!job) {
      throw new Error(
        `Preconfigured Job of type '${stageConfigSkeleton.key}' not found in /jobs/preconfigured from gate.  ` +
          'Is the preconfigured job registered in orca?',
      );
    }

    const parameters = job?.parameters ?? [];
    const paramsWithDefaults = parameters.filter((p) => !isNil(p.defaultValue));
    const defaultParameterValues = fromPairs(paramsWithDefaults.map((p) => [p.name, p.defaultValue]));

    const { label, description, waitForCompletion, producesArtifacts } = job;

    // Apply job configuration from Gate to the skeleton
    const stageConfig: IStageTypeConfig = {
      ...stageConfigSkeleton,
      configuration: {
        ...stageConfigSkeleton.configuration,
        parameters,
        waitForCompletion,
      },
      defaults: {
        parameters: defaultParameterValues,
      },
      description,
      label,
      producesArtifacts,
    };

    this.registerStage(stageConfig);
    return stageConfig;
  }

  public registerArtifactKind(
    artifactKindConfig: IArtifactKindConfig,
  ): ComponentType<IArtifactEditorProps> | SFC<IArtifactEditorProps> {
    this.artifactKinds.push(artifactKindConfig);
    return artifactKindConfig.editCmp;
  }

  public getExecutionTransformers(): ITransformer[] {
    return this.transformers;
  }

  public getNotificationTypes(): INotificationTypeConfig[] {
    return cloneDeep(this.notificationTypes);
  }

  public getTriggerTypes(): ITriggerTypeConfig[] {
    return cloneDeep(this.triggerTypes);
  }

  public getStageTypes(): IStageTypeConfig[] {
    return cloneDeep(this.stageTypes);
  }

  public getMatchArtifactKinds(): IArtifactKindConfig[] {
    return cloneDeep(this.artifactKinds.filter((k) => k.isMatch));
  }

  public getDefaultArtifactKinds(): IArtifactKindConfig[] {
    return cloneDeep(this.artifactKinds.filter((k) => k.isDefault));
  }

  public getCustomArtifactKind(): IArtifactKindConfig {
    return cloneDeep(this.artifactKinds.find((k) => k.key === 'custom'));
  }

  private getCloudProvidersForStage(
    type: IStageTypeConfig,
    allStageTypes: IStageTypeConfig[],
    accounts: IAccountDetails[],
  ): string[] {
    const providersFromAccounts = uniq(accounts.map((acc) => acc.cloudProvider));
    let providersFromStage: string[] = [];
    if (type.providesFor) {
      providersFromStage = type.providesFor;
    } else if (type.cloudProvider) {
      providersFromStage = [type.cloudProvider];
    } else if (type.useBaseProvider) {
      const stageProviders: IStageTypeConfig[] = allStageTypes.filter((s) => s.provides === type.key);
      stageProviders.forEach((sp) => {
        if (sp.providesFor) {
          providersFromStage = providersFromStage.concat(sp.providesFor);
        } else {
          providersFromStage.push(sp.cloudProvider);
        }
      });
    } else {
      providersFromStage = providersFromAccounts.slice(0);
    }

    // Remove a provider if none of the given accounts support the stage type.
    providersFromStage = providersFromStage.filter((providerKey: string) => {
      const providerAccounts = accounts.filter((acc) => acc.cloudProvider === providerKey);
      return !!providerAccounts.find((acc) => {
        const provider = CloudProviderRegistry.getProvider(acc.cloudProvider);
        return !isExcludedStageType(type, provider);
      });
    });

    // Docker Bake is wedged in here because it doesn't really fit our existing cloud provider paradigm
    if (SETTINGS.feature.dockerBake && type.key === 'bake') {
      providersFromAccounts.push('docker');
    }

    return intersection(providersFromAccounts, providersFromStage);
  }

  public getConfigurableStageTypes(accounts?: IAccountDetails[]): IStageTypeConfig[] {
    const providers: string[] = isNil(accounts) ? [] : Array.from(new Set(accounts.map((a) => a.cloudProvider)));
    const allStageTypes = this.getStageTypes();
    let configurableStageTypes = allStageTypes.filter((stageType) => !stageType.synthetic && !stageType.provides);
    if (providers.length === 0) {
      return configurableStageTypes;
    }
    configurableStageTypes.forEach(
      (type) => (type.cloudProviders = this.getCloudProvidersForStage(type, allStageTypes, accounts)),
    );
    configurableStageTypes = configurableStageTypes.filter((type) => {
      return !accounts.every((a) => {
        const p = CloudProviderRegistry.getProvider(a.cloudProvider);
        return isExcludedStageType(type, p);
      });
    });
    return configurableStageTypes
      .filter((stageType) => stageType.cloudProviders.length)
      .sort((a, b) => a.label.localeCompare(b.label));
  }

  public getProvidersFor(key: string): IStageTypeConfig[] {
    // because the key might be the implementation itself, determine the base key, then get every provider for it
    let baseKey = key;
    const stageTypes = this.getStageTypes();
    const candidates = stageTypes.filter((stageType: IStageTypeConfig) => {
      return stageType.provides && (stageType.provides === key || stageType.key === key || stageType.alias === key);
    });
    if (candidates.length) {
      baseKey = candidates[0].provides;
    }
    return this.getStageTypes().filter((stageType) => {
      return stageType.provides && stageType.provides === baseKey;
    });
  }

  public getNotificationConfig(type: string): INotificationTypeConfig {
    return this.getNotificationTypes().find((notificationType) => notificationType.key === type);
  }

  public getTriggerConfig(type: string): ITriggerTypeConfig {
    return this.getTriggerTypes().find((triggerType) => triggerType.key === type);
  }

  public overrideManualExecutionComponent(
    triggerType: string,
    component: React.ComponentType<ITriggerTemplateComponentProps>,
  ): void {
    const triggerConfig = this.triggerTypes.find((t) => t.key === triggerType);
    if (triggerConfig) {
      triggerConfig.manualExecutionComponent = component;
    }
  }

  /**
   * Checks stage.type against stageType.alias to match stages that may have run as a legacy type.
   * StageTypes set alias='legacyName' for backwards compatibility
   * @param stage
   */
  private checkAliasedStageTypes(stage: IStage): IStageTypeConfig {
    const aliasedMatches = this.getStageTypes().filter((stageType) => stageType.alias === stage.type);
    if (aliasedMatches.length === 1) {
      return aliasedMatches[0];
    }
    return null;
  }

  /**
   * Checks stage.alias against stageType.key to gracefully degrade redirected stages
   * For stages that don't actually exist in orca, if we couldn't find a match for them in deck either
   * (i.e. deprecated/deleted) this allows us to fallback to the stage type that actually ran in orca
   * @param stage
   */
  private checkAliasFallback(stage: IStage): IStageTypeConfig {
    if (stage.alias) {
      // Allow fallback to an exact match with stage.alias
      const aliasMatches = this.getStageTypes().filter((stageType) => stageType.key === stage.alias);
      if (aliasMatches.length === 1) {
        return aliasMatches[0];
      }
    }
    return null;
  }

  public getStageConfig(stage: IStage): IStageTypeConfig {
    if (!stage || !stage.type) {
      return null;
    }
    const matches = this.getStageTypes().filter((stageType) => {
      return stageType.key === stage.type || stageType.provides === stage.type;
    });

    switch (matches.length) {
      case 0: {
        // There are really only 2 usages for 'alias':
        // - to allow deck to still find a match for legacy stage types
        // - to have stages that actually run as their 'alias' in orca (addAliasToConfig) because their 'key' doesn't actually exist
        const aliasMatch = this.checkAliasedStageTypes(stage) || this.checkAliasFallback(stage);
        const unmatchedStageType = this.getStageTypes().find((s) => s.key === 'unmatched');
        return aliasMatch ?? unmatchedStageType;
      }
      case 1:
        return matches[0];
      default: {
        // More than one stage definition matched the stage's 'type' field.
        // Try to narrow it down by cloud provider.
        const provider = PipelineRegistry.resolveCloudProvider(stage);
        const matchesThisCloudProvider = matches.find((stageType) => stageType.cloudProvider === provider);
        const matchesAnyCloudProvider = matches.find((stageType) => !!stageType.cloudProvider);
        return matchesThisCloudProvider ?? matchesAnyCloudProvider ?? matches[0];
      }
    }
  }

  // IStage doesn't have a cloudProvider field yet many stage configs are setting it.
  // Some stages (RunJob, ?) are only setting the cloudProvider field in stage.context.
  private static resolveCloudProvider(stage: IStage): string {
    return (
      stage.cloudProvider ??
      stage.cloudProviderType ??
      stage.context?.cloudProvider ??
      stage.context?.cloudProviderType ??
      'aws'
    );
  }

  private getManualExecutionComponent(
    config: IStageOrTriggerTypeConfig,
  ): React.ComponentType<ITriggerTemplateComponentProps> {
    if (config && config.manualExecutionComponent) {
      return config.manualExecutionComponent;
    }
    return null;
  }

  public getManualExecutionComponentForTriggerType(
    triggerType: string,
  ): React.ComponentType<ITriggerTemplateComponentProps> {
    return this.getManualExecutionComponent(this.getTriggerConfig(triggerType));
  }

  public hasManualExecutionComponentForTriggerType(triggerType: string): boolean {
    return this.getManualExecutionComponent(this.getTriggerConfig(triggerType)) !== null;
  }

  public getManualExecutionComponentForStage(stage: IStage): React.ComponentType<ITriggerTemplateComponentProps> {
    return this.getStageConfig(stage).manualExecutionComponent;
  }
}

function isExcludedStageType(type: IStageTypeConfig, provider: ICloudProviderConfig) {
  if (!provider || !provider.unsupportedStageTypes) {
    return false;
  }
  return provider.unsupportedStageTypes.indexOf(type.key) > -1;
}
