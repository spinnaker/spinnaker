import { cloneDeep } from 'lodash';
import React from 'react';

import { IServerGroupCommand } from '../serverGroup';

export interface IDeploymentStrategyAdditionalFieldsProps {
  command: IServerGroupCommand;
  onChange: (key: string, value: any) => void;
}

export interface IDeploymentStrategy {
  key: string;
  label: string;
  description: string;
  providerRestricted?: boolean;
  additionalFields?: string[];
  additionalFieldsTemplateUrl?: string;
  AdditionalFieldsComponent?: React.ComponentType<IDeploymentStrategyAdditionalFieldsProps>;
  initializationMethod?: (command: any) => void;
}

export class DeploymentStrategyRegistrar {
  private strategies: IDeploymentStrategy[] = [];
  private providerRegistry: { [key: string]: string[] } = {};

  public registerStrategy(strategy: IDeploymentStrategy): void {
    this.strategies.push(strategy);
    this.configureProviderRegistryEntry(strategy.key);
  }

  public listStrategies(cloudProvider: string): IDeploymentStrategy[] {
    return this.strategies
      .filter((s) => !s.providerRestricted || this.providerRegistry[s.key].includes(cloudProvider))
      .map(cloneDeep);
  }

  public registerProvider(provider: string, strategies: string[]) {
    strategies.forEach((strategy) => {
      this.configureProviderRegistryEntry(strategy);
      this.providerRegistry[strategy].push(provider);
    });
  }

  public getStrategy(key: string): IDeploymentStrategy {
    return this.strategies.find((s) => s.key === key);
  }

  private configureProviderRegistryEntry(key: string): void {
    if (!this.providerRegistry[key]) {
      this.providerRegistry[key] = [];
    }
  }
}

export const DeploymentStrategyRegistry = new DeploymentStrategyRegistrar();
