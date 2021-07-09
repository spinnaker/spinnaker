import { SearchResultType, searchResultTypeRegistry } from '../search/searchResult';
import { IDeckPlugin, registerPluginExtensions } from './deck.plugin';
import { HelpContentsRegistry } from '../help';
import { Registry } from '../registry';

describe('deck plugin registerPluginExtensions', () => {
  it('returns a promise', async () => {
    const promiseLike = registerPluginExtensions({});
    expect(promiseLike).toBeDefined();
    expect(promiseLike.then).toBeDefined();
    expect(typeof promiseLike.then).toBe('function');
  });

  it('returns a promise that resolves to the return value of initialize', async () => {
    const result = await registerPluginExtensions({ initialize: () => 'anything' });
    expect(result).toBe('anything');
  });

  it('returns a promise that unwraps a promise returned by initialize', async () => {
    const result = await registerPluginExtensions({ initialize: () => Promise.resolve('anything') });
    expect(result).toBe('anything');
  });

  it('initialize() receives the IDeckPlugin object as the first argument', async () => {
    const plugin: IDeckPlugin = { initialize: jasmine.createSpy('initialize') };
    await registerPluginExtensions(plugin);
    expect(plugin.initialize).toHaveBeenCalledWith(plugin);
  });

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

  it('should register search', async () => {
    const registerSpy = spyOn(searchResultTypeRegistry, 'register');
    const search = {} as SearchResultType;
    registerPluginExtensions({ search: [search] });
    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy).toHaveBeenCalledWith(search);
  });
});
