import { ServerGroupBasicSettingsComponent } from './ServerGroupBasicSettings';

describe('Titus ServerGroupBasicSettings', () => {
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
        setFieldValue: () => undefined,
        values: {
          credentials: 'test',
          freeFormDetails: '',
          region: 'us-east-1',
          selectedProvider: 'titus',
          stack: 'main',
        },
      },
      router: {},
      stateParams: {},
      stateService,
    } as any);

    (component as any).navigateToLatestServerGroup();

    expect(stateService.go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'test',
      provider: 'titus',
      region: 'us-east-1',
      serverGroup: 'app-main-v001',
    });
  });
});
