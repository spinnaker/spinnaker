import { setDirectRouter } from './directRouter';
import { UrlBuilder } from './UrlBuilder';
import { Registry } from '../registry';

describe('UrlBuilder', () => {
  afterEach(() => setDirectRouter(null));

  it('builds metadata URLs with the direct router state service', () => {
    const href = jasmine.createSpy('href').and.returnValue('/applications/payments');
    const stateService = { href } as any;
    const build = jasmine
      .createSpy('build')
      .and.callFake((input: any, injectedStateService: any) =>
        injectedStateService.href(
          'home.applications.application',
          { application: input.application },
          { inherit: false },
        ),
      );
    Registry.urlBuilder.register('direct-router-test', { build });
    setDirectRouter({ stateService } as any);

    const input = { application: 'payments', type: 'direct-router-test' };
    const result = UrlBuilder.buildFromMetadata(input);

    expect(build).toHaveBeenCalledWith(input, stateService);
    expect(href).toHaveBeenCalledWith('home.applications.application', { application: 'payments' }, { inherit: false });
    expect(result).toBe('/applications/payments');
  });
});
