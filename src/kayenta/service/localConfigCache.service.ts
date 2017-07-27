import { ICanaryConfig } from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';

const atlasCanaryConfig = require('kayenta/scratch/atlas_canary_config.json');
const stackdriverCanaryConfig = require('kayenta/scratch/stackdriver_canary_config.json');

/*
* For development only.
*/
class LocalConfigCache {

  private configs = new Set<ICanaryConfig>();

  constructor(...initialConfigs: ICanaryConfig[]) {
    initialConfigs.forEach(config => {
      this.configs.add(config);
    });
  }

  public getCanaryConfigById(id: string): Promise<ICanaryConfig> {
    return Promise.resolve(Array.from(this.configs).find(c => c.name === id));
  }

  public getCanaryConfigSummaries(): Promise<ICanaryConfigSummary[]> {
    const summaries = Array.from(this.configs).map(config => ({
      updatedTimestamp: config.updatedTimestamp,
      updatedTimestampIso: config.updatedTimestampIso,
      name: config.name,
    }));
    return Promise.resolve(summaries);
  }

  public createCanaryConfig(config: ICanaryConfig): Promise<{id: string}> {
    this.configs.add(config);
    return Promise.resolve({id: config.name});
  }

  public updateCanaryConfig(config: ICanaryConfig): Promise<{id: string}> {
    return this.deleteCanaryConfig(config.name)
      .then(() => this.configs.add(config))
      .then(() => ({id: config.name}));
  }

  public deleteCanaryConfig(id: string): Promise<void> {
    const config = Array.from(this.configs).find(c => c.name === id);
    this.configs.delete(config);
    return Promise.resolve(null);
  }
}

export const localConfigCache = new LocalConfigCache(atlasCanaryConfig, stackdriverCanaryConfig);
