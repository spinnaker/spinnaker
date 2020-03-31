import { registerPluginExtensions } from './deck.plugin';
import { HelpContentsRegistry } from '../help';
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

  it('should register help', async () => {
    const registerSpy = spyOn(HelpContentsRegistry, 'register');
    const help = { key: 'value', key2: 'value2' };
    registerPluginExtensions({ help });
    expect(registerSpy).toHaveBeenCalledTimes(2);
    expect(registerSpy.calls.first().args).toEqual(['key', 'value']);
    expect(registerSpy.calls.mostRecent().args).toEqual(['key2', 'value2']);
  });
});
