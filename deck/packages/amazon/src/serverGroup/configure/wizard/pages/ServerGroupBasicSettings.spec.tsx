import { ServerGroupBasicSettingsComponent } from './ServerGroupBasicSettings';

describe('Amazon ServerGroupBasicSettings', () => {
  it('opens the latest server group through the injected state service', () => {
    const stateService = { go: jasmine.createSpy('go'), is: () => true };
    const component = new ServerGroupBasicSettingsComponent({
      app: {
        clusters: [],
        name: 'app',
        serverGroups: {
          data: [{ account: 'test', cluster: 'app-main', createdTime: 1, name: 'app-main-v001', region: 'us-east-1' }],
        },
      },
      formik: {
        values: {
          amiName: 'ami',
          credentials: 'test',
          freeFormDetails: '',
          region: 'us-east-1',
          selectedProvider: 'aws',
          stack: 'main',
          viewState: { imageId: 'ami-123' },
        },
      },
      router: {},
      stateParams: {},
      stateService,
    } as any);

    (component as any).navigateToLatestServerGroup();

    expect(stateService.go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'test',
      provider: 'aws',
      region: 'us-east-1',
      serverGroup: 'app-main-v001',
    });
  });
});
