import { registerPluginExtensions } from './deck.plugin';
import { Registry } from '../registry';

describe('deck plugin registerPluginExtensions', () => {
  it('should register stages', async () => {
    const registerSpy = spyOn(Registry.pipeline, 'registerStage');
    const stage = { key: 'test' };
    registerPluginExtensions({ stages: [stage] });
    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy).toHaveBeenCalledWith(stage);
  });

  it('should register preconfigured job stages', async () => {
    const registerSpy = spyOn(Registry.pipeline, 'registerPreconfiguredJobStage');
    const stage = { key: 'test' };
    registerPluginExtensions({ preconfiguredJobStages: [stage] });
    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy).toHaveBeenCalledWith(stage);
  });
});
