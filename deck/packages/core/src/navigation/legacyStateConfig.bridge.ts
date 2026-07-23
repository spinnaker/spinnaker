import type { IAngularStatic } from 'angular';

import type { ApplicationStateProvider } from '../application/application.state.provider';
import type { StateConfigProvider } from './state.provider';

type InjectableConfig = Array<string | Function> | Function;

interface CapturedConfig {
  deps: string[];
  fn: Function;
}

const capturedConfigs: CapturedConfig[] = [];
const angular = require('angular') as IAngularStatic;
let patched = false;

function parseConfig(config: InjectableConfig): CapturedConfig | null {
  if (Array.isArray(config)) {
    const deps = config.slice(0, -1) as string[];
    const fn = config[config.length - 1];

    if (typeof fn !== 'function') {
      return null;
    }

    return { deps, fn };
  }

  return null;
}

function isStateConfig({ deps }: CapturedConfig): boolean {
  return deps.includes('stateConfigProvider') || deps.includes('applicationStateProvider');
}

export function captureLegacyStateConfigs(): void {
  if (patched) {
    return;
  }

  patched = true;
  const originalModule = angular.module;

  angular.module = function patchedAngularModule(...args: any[]) {
    const angularModule = originalModule.apply(this, args);
    const originalConfig = angularModule.config;

    if (!(angularModule as any).__spinnakerStateConfigCapturePatched) {
      (angularModule as any).__spinnakerStateConfigCapturePatched = true;
      angularModule.config = function patchedConfig(config: InjectableConfig) {
        const parsed = parseConfig(config);

        if (parsed && isStateConfig(parsed)) {
          capturedConfigs.push(parsed);
        }

        return originalConfig.apply(this, arguments as any);
      } as any;
    }

    return angularModule;
  } as typeof angular.module;
}

export function applyLegacyStateConfigs(
  stateConfigProvider: StateConfigProvider,
  applicationStateProvider: ApplicationStateProvider | null,
): void {
  capturedConfigs.forEach(({ deps, fn }) => {
    const args = deps.map((dep) => {
      if (dep === 'stateConfigProvider') {
        return stateConfigProvider;
      }
      if (dep === 'applicationStateProvider') {
        return applicationStateProvider;
      }
      return undefined;
    });

    fn(...args);
  });
}

captureLegacyStateConfigs();
