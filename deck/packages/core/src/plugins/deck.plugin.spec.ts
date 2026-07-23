import type { IDeckPlugin } from './deck.plugin';
import { registerPluginExtensions } from './deck.plugin';
import type { IStageTypeConfig } from '../domain';
import { HelpContentsRegistry } from '../help';
import { Registry } from '../registry';
import type { SearchResultType } from '../search/searchResult';
import { searchResultTypeRegistry } from '../search/searchResult';

describe('deck plugin registerPluginExtensions', () => {
  it('returns a promise', async () => {
    const promiseLike = registerPluginExtensions({});
    expect(promiseLike).toBeDefined();
    expect(promiseLike.then).toBeDefined();
    expect(typeof promiseLike.then).toBe('function');
    await promiseLike;
  });

  it('waits for asynchronous initialize work', async () => {
    let resolveInitialize!: () => void;
    const initializePromise = new Promise<void>((resolve) => (resolveInitialize = resolve));
    let initializeStarted = false;
    let settled = false;

    const registrationPromise = registerPluginExtensions({
      initialize: () => {
        initializeStarted = true;
        return initializePromise;
      },
    }).then(() => (settled = true));

    expect(initializeStarted).toBeTrue();
    await Promise.resolve();

    expect(settled).toBeFalse();
    resolveInitialize();
    await registrationPromise;
    expect(settled).toBeTrue();
  });

  it('initialize() receives the IDeckPlugin object as the first argument', async () => {
    const plugin: IDeckPlugin = { initialize: jasmine.createSpy('initialize') };
    const registrationPromise = registerPluginExtensions(plugin);

    expect(plugin.initialize).toHaveBeenCalledWith(plugin);
    await registrationPromise;
  });

  it('registers stages synchronously', async () => {
    const registerSpy = spyOn(Registry.pipeline, 'registerStage');
    const stage = { key: 'test' };
    const registrationPromise = registerPluginExtensions({ stages: [stage] });

    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy).toHaveBeenCalledWith(stage);
    await registrationPromise;
  });

  it('registers preconfigured job stages synchronously', async () => {
    const registerSpy = spyOn(Registry.pipeline, 'registerPreconfiguredJobStage');
    const stage = { key: 'test' };
    const registrationPromise = registerPluginExtensions({ preconfiguredJobStages: [stage] });

    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy).toHaveBeenCalledWith(stage);
    await registrationPromise;
  });

  it('waits for asynchronous preconfigured job stage registration', async () => {
    let resolveRegistration!: (stage: IStageTypeConfig) => void;
    const stage = { key: 'test' } as IStageTypeConfig;
    const deferredRegistration = new Promise<IStageTypeConfig>((resolve) => (resolveRegistration = resolve));
    spyOn(Registry.pipeline, 'registerPreconfiguredJobStage').and.returnValue(deferredRegistration);
    let settled = false;

    const registrationPromise = registerPluginExtensions({ preconfiguredJobStages: [stage] }).then(
      () => (settled = true),
    );
    await Promise.resolve();

    expect(settled).toBeFalse();
    resolveRegistration(stage);
    await registrationPromise;
    expect(settled).toBeTrue();
  });

  it('rejects when asynchronous preconfigured job stage registration fails', async () => {
    let rejectRegistration!: (reason?: any) => void;
    const stage = { key: 'test' } as IStageTypeConfig;
    const registrationError = new Error('registration failed');
    const deferredRegistration = new Promise<IStageTypeConfig>((_, reject) => (rejectRegistration = reject));
    const observedRegistration = deferredRegistration.catch(() => undefined);
    spyOn(Registry.pipeline, 'registerPreconfiguredJobStage').and.returnValue(deferredRegistration);

    const registrationPromise = registerPluginExtensions({ preconfiguredJobStages: [stage] });
    rejectRegistration(registrationError);

    await expectAsync(registrationPromise).toBeRejectedWith(registrationError);
    await observedRegistration;
  });

  it('waits for pending asynchronous work before rejecting a later synchronous registration failure', async () => {
    let resolveRegistration!: (stage: IStageTypeConfig) => void;
    const stage = { key: 'test' } as IStageTypeConfig;
    const registrationError = new Error('help registration failed');
    const deferredRegistration = new Promise<IStageTypeConfig>((resolve) => (resolveRegistration = resolve));
    spyOn(Registry.pipeline, 'registerPreconfiguredJobStage').and.returnValue(deferredRegistration);
    spyOn(HelpContentsRegistry, 'register').and.throwError(registrationError);
    let settled = false;

    const registrationPromise = registerPluginExtensions({
      preconfiguredJobStages: [stage],
      help: { key: 'value' },
    });
    const rejectionExpectation = expectAsync(registrationPromise).toBeRejectedWith(registrationError);
    const observedRegistration = registrationPromise.then(
      () => (settled = true),
      () => (settled = true),
    );
    await Promise.resolve();

    expect(settled).toBeFalse();
    resolveRegistration(stage);
    await rejectionExpectation;
    await observedRegistration;
  });

  it('registers help synchronously', async () => {
    const registerSpy = spyOn(HelpContentsRegistry, 'register');
    const help = { key: 'value', key2: 'value2' };
    const registrationPromise = registerPluginExtensions({ help });

    expect(registerSpy).toHaveBeenCalledTimes(2);
    expect(registerSpy.calls.first().args).toEqual(['key', 'value']);
    expect(registerSpy.calls.mostRecent().args).toEqual(['key2', 'value2']);
    await registrationPromise;
  });

  it('registers search synchronously', async () => {
    const registerSpy = spyOn(searchResultTypeRegistry, 'register');
    const search = {} as SearchResultType;
    const registrationPromise = registerPluginExtensions({ search: [search] });

    expect(registerSpy).toHaveBeenCalledTimes(1);
    expect(registerSpy).toHaveBeenCalledWith(search);
    await registrationPromise;
  });
});
