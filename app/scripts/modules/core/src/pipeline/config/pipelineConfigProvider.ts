import { auto, IServiceProvider, module } from 'angular';
import { uniq, isNil, cloneDeep, intersection, memoize } from 'lodash';
import { $log } from 'ngimport';

import { Application } from 'core/application/application.model';
import {
  IExecution,
  IStage,
  ITriggerTypeConfig,
  IStageTypeConfig,
  IArtifactKindConfig,
  IStageOrTriggerTypeConfig,
} from 'core/domain';
import {
  CLOUD_PROVIDER_REGISTRY,
  CloudProviderRegistry,
  ICloudProviderConfig,
} from 'core/cloudProvider/cloudProvider.registry';
import { SETTINGS } from 'core/config/settings';

import { IAccountDetails } from 'core/account/account.service';

export interface ITransformer {
  transform: (application: Application, execution: IExecution) => void;
}

export class PipelineConfigProvider implements IServiceProvider {
  private $injector: auto.IInjectorService;

  private triggerTypes: ITriggerTypeConfig[] = [];
  private stageTypes: IStageTypeConfig[] = [];
  private transformers: ITransformer[] = [];
  private artifactKinds: IArtifactKindConfig[] = [];

  constructor(private cloudProviderRegistryProvider: CloudProviderRegistry) {
    this.getStageConfig = memoize(this.getStageConfig.bind(this), (stage: IStage) =>
      [stage ? stage.type : '', stage ? stage.cloudProvider || stage.cloudProviderType || 'aws' : ''].join(':'),
    );
  }

  private normalizeStageTypes(): void {
    this.stageTypes
      .filter(stageType => {
        return stageType.provides;
      })
      .forEach(stageType => {
        const parent = this.stageTypes.find(parentType => {
          return parentType.key === stageType.provides && !parentType.provides;
        });
        if (parent) {
          stageType.label = stageType.label || parent.label;
          stageType.description = stageType.description || parent.description;
          stageType.key = stageType.key || parent.key;
          stageType.manualExecutionHandler = stageType.manualExecutionHandler || parent.manualExecutionHandler;

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
    this.stageTypes.push(stageConfig);
    this.normalizeStageTypes();
  }

  public registerArtifactKind(artifactKindConfig: IArtifactKindConfig): void {
    this.artifactKinds.push(artifactKindConfig);
  }

  public getExecutionTransformers(): ITransformer[] {
    return this.transformers;
  }

  public getTriggerTypes(): ITriggerTypeConfig[] {
    return cloneDeep(this.triggerTypes);
  }

  public getStageTypes(): IStageTypeConfig[] {
    return cloneDeep(this.stageTypes);
  }

  public getArtifactKinds(): IArtifactKindConfig[] {
    return cloneDeep(this.artifactKinds);
  }

  private getCloudProvidersForStage(
    type: IStageTypeConfig,
    allStageTypes: IStageTypeConfig[],
    accounts: IAccountDetails[],
  ): string[] {
    const providersFromAccounts = uniq(accounts.map(acc => acc.cloudProvider));
    let providersFromStage: string[] = [];
    if (type.providesFor) {
      providersFromStage = type.providesFor;
    } else if (type.cloudProvider) {
      providersFromStage = [type.cloudProvider];
    } else if (type.useBaseProvider) {
      const stageProviders: IStageTypeConfig[] = allStageTypes.filter(s => s.provides === type.key);
      stageProviders.forEach(sp => {
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
      const providerAccounts = accounts.filter(acc => acc.cloudProvider === providerKey);
      return !!providerAccounts.find(acc => {
        const provider = this.cloudProviderRegistryProvider.getProvider(acc.cloudProvider, acc.skin);
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
    const providers: string[] = isNil(accounts) ? [] : Array.from(new Set(accounts.map(a => a.cloudProvider)));
    const allStageTypes = this.getStageTypes();
    let configurableStageTypes = allStageTypes.filter(stageType => !stageType.synthetic && !stageType.provides);
    if (providers.length === 0) {
      return configurableStageTypes;
    }
    configurableStageTypes.forEach(
      type => (type.cloudProviders = this.getCloudProvidersForStage(type, allStageTypes, accounts)),
    );
    configurableStageTypes = configurableStageTypes.filter(type => {
      return !accounts.every(a => {
        const p = this.cloudProviderRegistryProvider.getProvider(a.cloudProvider, a.skin);
        return isExcludedStageType(type, p);
      });
    });
    return configurableStageTypes
      .filter(stageType => stageType.cloudProviders.length)
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
    return this.getStageTypes().filter(stageType => {
      return stageType.provides && stageType.provides === baseKey;
    });
  }

  public getTriggerConfig(type: string): ITriggerTypeConfig {
    return this.getTriggerTypes().find(triggerType => triggerType.key === type);
  }

  public overrideManualExecutionHandler(triggerType: string, handlerName: string): void {
    const triggerConfig = this.triggerTypes.find(t => t.key === triggerType);
    if (triggerConfig) {
      triggerConfig.manualExecutionHandler = handlerName;
    }
  }

  public getStageConfig(stage: IStage): IStageTypeConfig {
    if (!stage || !stage.type) {
      return null;
    }
    const matches = this.getStageTypes().filter(stageType => {
      return stageType.key === stage.type || stageType.provides === stage.type || stageType.alias === stage.type;
    });

    switch (matches.length) {
      case 0:
        return this.getStageTypes().find(s => s.key === 'unmatched') || null;
      case 1:
        return matches[0];
      default:
        const provider = stage.cloudProvider || stage.cloudProviderType || 'aws';
        const matchesForStageCloudProvider = matches.filter(stageType => {
          return stageType.cloudProvider === provider;
        });

        if (!matchesForStageCloudProvider.length) {
          return (
            matches.find(stageType => {
              return !!stageType.cloudProvider;
            }) || null
          );
        } else {
          return matchesForStageCloudProvider[0];
        }
    }
  }

  private getManualExecutionHandler(config: IStageOrTriggerTypeConfig): string {
    if (config && config.manualExecutionHandler) {
      if (this.$injector.has(config.manualExecutionHandler)) {
        return this.$injector.get<string>(config.manualExecutionHandler);
      } else {
        $log.warn(`Could not find execution handler '${config.manualExecutionHandler}' for type '${config.key}'`);
      }
    }
    return null;
  }

  public getManualExecutionHandlerForTriggerType(triggerType: string): string {
    return this.getManualExecutionHandler(this.getTriggerConfig(triggerType));
  }

  public hasManualExecutionHandlerForTriggerType(triggerType: string): boolean {
    return this.getManualExecutionHandler(this.getTriggerConfig(triggerType)) !== null;
  }

  public getManualExecutionHandlerForStage(stage: IStage): string {
    // the handler approach for triggers is a bit overblown; for pipeline stages, just use a template
    // should(?) be easier to convert to React later, as we'll just convert it to a React component class...
    return this.getStageConfig(stage).manualExecutionHandler;
  }

  public $get($injector: auto.IInjectorService): PipelineConfigProvider {
    this.$injector = $injector;
    return this;
  }
}

function isExcludedStageType(type: IStageTypeConfig, provider: ICloudProviderConfig) {
  if (!provider || !provider.unsupportedStageTypes) {
    return false;
  }
  return provider.unsupportedStageTypes.indexOf(type.key) > -1;
}

export const PIPELINE_CONFIG_PROVIDER = 'spinnaker.core.pipeline.config.configProvider';
module(PIPELINE_CONFIG_PROVIDER, [CLOUD_PROVIDER_REGISTRY]).provider('pipelineConfig', PipelineConfigProvider);
