import { ICanaryConfig } from '../domain/ICanaryConfig';
import { ICanaryConfigSummary } from '../domain/ICanaryConfigSummary';
import { IJudge } from '../domain/IJudge';
import { IKayentaAccount, KayentaAccountType } from '../domain/IKayentaAccount';
import { UUIDGenerator } from '@spinnaker/core';
import { ICanaryConfigUpdateResponse } from '../domain/ICanaryConfigUpdateResponse';

const atlasCanaryConfig = require('kayenta/scratch/atlas_canary_config.json');
const stackdriverCanaryConfig = require('kayenta/scratch/stackdriver_canary_config.json');

/*
* For development only.
*/
export class LocalConfigStore {

  private configs = new Set<ICanaryConfig>();

  constructor(...initialConfigs: ICanaryConfig[]) {
    initialConfigs.forEach(config => {
      this.configs.add(config);
    });
  }

  public getCanaryConfigById(id: string): Promise<ICanaryConfig> {
    const config = Array.from(this.configs).find(c => c.id === id);
    if (config) {
      return Promise.resolve(config);
    } else {
      return Promise.reject({ data: { message: 'Config not found.' }});
    }
  }

  public getCanaryConfigSummaries(): Promise<ICanaryConfigSummary[]> {
    const summaries = Array.from(this.configs).map(config => ({
      id: config.id,
      updatedTimestamp: config.updatedTimestamp,
      updatedTimestampIso: config.updatedTimestampIso,
      name: config.name,
      applications: ['myapp']
    }));
    return Promise.resolve(summaries);
  }

  public createCanaryConfig(config: ICanaryConfig): Promise<ICanaryConfigUpdateResponse> {
    const id = UUIDGenerator.generateUuid();
    this.configs.add({
      ...config,
      id,
    });
    return Promise.resolve({ canaryConfigId: id });
  }

  public updateCanaryConfig(config: ICanaryConfig): Promise<ICanaryConfigUpdateResponse> {
    return this.deleteCanaryConfig(config.id)
      .then(() => this.createCanaryConfig(config));
  }

  public deleteCanaryConfig(id: string): Promise<void> {
    const config = Array.from(this.configs).find(c => c.id === id);
    this.configs.delete(config);
    return Promise.resolve(null);
  }

  public listJudges(): Promise<IJudge[]> {
    return Promise.resolve([
      { name: 'dredd-v1.0', visible: false },
      { name: 'NetflixACAJudge-v1.0', visible: true },
    ]);
  }

  public listKayentaAccounts(): Promise<IKayentaAccount[]> {
    return Promise.resolve([
      {
        name: 'my-google-account',
        type: 'google',
        supportedTypes: [
          KayentaAccountType.ObjectStore,
          KayentaAccountType.MetricsStore,
          KayentaAccountType.ConfigurationStore,
        ],
      },
      {
        name: 'my-other-google-account',
        type: 'google',
        supportedTypes: [KayentaAccountType.MetricsStore],
      },
    ]);
  }
}

export const localConfigStore = new LocalConfigStore(atlasCanaryConfig, stackdriverCanaryConfig);
