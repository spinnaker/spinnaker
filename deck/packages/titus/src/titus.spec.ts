import { CloudProviderRegistry, SETTINGS } from '@spinnaker/core';

import { titusCloneServerGroupStage } from './pipeline/stages/cloneServerGroup/titusCloneServerGroupStage';
import { titusDestroyAsgStage } from './pipeline/stages/destroyAsg/titusDestroyAsgStage';
import { titusDisableAsgStage } from './pipeline/stages/disableAsg/titusDisableAsgStage';
import { titusDisableClusterStage } from './pipeline/stages/disableCluster/titusDisableClusterStage';
import { titusEnableAsgStage } from './pipeline/stages/enableAsg/titusEnableAsgStage';
import { titusFindAmiStage } from './pipeline/stages/findAmi/titusFindAmiStage';
import { titusResizeAsgStage } from './pipeline/stages/resizeAsg/titusResizeAsgStage';
import { titusScaleDownClusterStage } from './pipeline/stages/scaleDownCluster/titusScaleDownClusterStage';
import { titusShrinkClusterStage } from './pipeline/stages/shrinkCluster/titusShrinkClusterStage';

describe('Titus package entrypoint', () => {
  it('loads successfully', () => {
    SETTINGS.providers.titus = true;
    expect(() => require('./index')).not.toThrow();
  });

  it('does not expose Angular module tokens', () => {
    SETTINGS.providers.titus = true;
    const titusPackage = require('./index');
    const angularModuleToken = ['TITUS', 'MODULE'].join('_');
    const reactModuleToken = ['TITUS', 'REACT', 'MODULE'].join('_');

    expect((titusPackage as any)[angularModuleToken]).toBeUndefined();
    expect((titusPackage as any)[reactModuleToken]).toBeUndefined();
  });

  it('registers non-Angular provider configuration', () => {
    SETTINGS.providers.titus = true;
    require('./index');
    require('./index').registerTitusProvider();

    expect(CloudProviderRegistry.getValue('titus', ['serverGroup.details', 'Template', 'Url'].join(''))).toBeNull();
    expect(CloudProviderRegistry.getValue('titus', ['serverGroup.details', 'Cont', 'roller'].join(''))).toBeNull();
    expect(typeof CloudProviderRegistry.getValue('titus', 'serverGroup.detailsGetter')).toBe('function');
    expect(CloudProviderRegistry.getValue('titus', 'serverGroup.detailsSections').length).toBeGreaterThan(0);
    expect(typeof CloudProviderRegistry.getValue('titus', 'serverGroup.transformer')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('titus', 'serverGroup.commandBuilder')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('titus', 'serverGroup.configurationService')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('titus', 'securityGroup.reader')).toBe('function');
  });

  [
    ['cloneServerGroup', titusCloneServerGroupStage],
    ['destroyServerGroup', titusDestroyAsgStage],
    ['disableServerGroup', titusDisableAsgStage],
    ['disableCluster', titusDisableClusterStage],
    ['enableServerGroup', titusEnableAsgStage],
    ['findImage', titusFindAmiStage],
    ['resizeServerGroup', titusResizeAsgStage],
    ['scaleDownCluster', titusScaleDownClusterStage],
    ['shrinkCluster', titusShrinkClusterStage],
  ].forEach(([stageType, providerConfig]: any[]) => {
    it(`registers React configuration form for ${stageType}`, () => {
      expect(providerConfig.provides).toBe(stageType);
      expect(providerConfig.cloudProvider).toBe('titus');
      expect(providerConfig.component).toEqual(jasmine.any(Function));
      expect(providerConfig.templateUrl).toBeUndefined();
    });
  });
});
