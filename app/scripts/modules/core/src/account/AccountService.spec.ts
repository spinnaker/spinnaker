import { mock } from 'angular';
import { mockHttpClient } from '../api/mock/jasmine';
import { MockHttpClient } from '../api/mock/mockHttpClient';

import { SETTINGS } from '../config/settings';
import { $rootScope } from 'ngimport';
import { CloudProviderRegistry } from '../cloudProvider';

import { AccountService, IAccount } from './AccountService';

function flush<T>(http: MockHttpClient, promise: PromiseLike<T>): Promise<T> {
  return http
    .flush()
    .then(() => setTimeout(() => $rootScope.$digest()))
    .then(() => promise);
}

describe('Service: AccountService', () => {
  beforeEach(mock.inject());
  beforeEach(() => AccountService.initialize());
  afterEach(SETTINGS.resetToOriginal);

  it('should filter the list of accounts by provider when supplied', async () => {
    const http = mockHttpClient();
    http
      .expectGET(`/credentials`)
      .withParams({ expand: true })
      .respond(200, [
        { name: 'test', type: 'aws' },
        { name: 'prod', type: 'aws' },
        { name: 'prod', type: 'gce' },
        { name: 'gce-test', type: 'gce' },
      ]);

    const accounts = await flush(http, AccountService.listAccounts('aws'));
    expect(accounts.length).toBe(2);
    expect(accounts.map((account: IAccount) => account.name)).toEqual(['test', 'prod']);
  });

  describe('getAllAccountDetailsForProvider', () => {
    it('should return details for each account', async () => {
      const http = mockHttpClient();
      http
        .expectGET('/credentials')
        .withParams({ expand: true })
        .respond(200, [
          { name: 'test', type: 'aws' },
          { name: 'prod', type: 'aws' },
        ]);

      const details = await flush(http, AccountService.getAllAccountDetailsForProvider('aws'));
      expect(details.length).toBe(2);
      expect(details[0].name).toBe('test');
      expect(details[1].name).toBe('prod');
    });

    it('should fall back to an empty array if an exception occurs when listing accounts', async () => {
      const http = mockHttpClient();
      http.expectGET('/credentials').withParams({ expand: true }).respond(429, null);

      const details = await flush(http, AccountService.getAllAccountDetailsForProvider('aws'));
      expect(details).toEqual([]);
    });
  });

  describe('listProviders', () => {
    const providers = [{ type: 'aws' }, { type: 'gce' }, { type: 'cf' }];
    const registeredProviders = ['aws', 'gce', 'cf'];

    const setupTest = () => {
      const http = mockHttpClient();
      http.expectGET('/credentials').withParams({ expand: true }).respond(200, providers);
      spyOn(CloudProviderRegistry, 'listRegisteredProviders').and.returnValue(registeredProviders);
      return http;
    };

    it('should list all providers when no application provided', async () => {
      const http = setupTest();
      const result = await flush(http, AccountService.listProviders());
      expect(result).toEqual(['aws', 'cf', 'gce']);
    });

    it('should filter out providers not registered', async () => {
      const http = mockHttpClient();
      http.expectGET('/credentials').withParams({ expand: true }).respond(200, providers.slice(0, 2));
      spyOn(CloudProviderRegistry, 'listRegisteredProviders').and.returnValue(registeredProviders.slice(0, 2));

      const result = await flush(http, AccountService.listProviders());
      expect(result).toEqual(['aws', 'gce']);
    });

    it('should fall back to the defaultProviders if none configured for the application', async () => {
      const http = setupTest();

      const application: any = { attributes: { cloudProviders: [] } };
      SETTINGS.defaultProviders = ['gce', 'cf'];
      const result = await flush(http, AccountService.listProviders(application));
      expect(result).toEqual(['cf', 'gce']);
    });

    it('should return the intersection of those configured for the application and those available from the server', async () => {
      const http = setupTest();

      const application: any = { attributes: { cloudProviders: ['gce', 'cf', 'unicron'] } };
      SETTINGS.defaultProviders = ['aws'];
      const result = await flush(http, AccountService.listProviders(application));
      expect(result).toEqual(['cf', 'gce']);
    });

    it('should return an empty array if none of the app providers are available from the server', async () => {
      const http = setupTest();

      const application: any = { attributes: { cloudProviders: ['lamp', 'ceiling', 'fan'] } };
      SETTINGS.defaultProviders = ['foo'];
      const result = await flush(http, AccountService.listProviders(application));
      expect(result).toEqual([]);
    });

    it('should fall back to all registered available providers if no defaults configured and none configured on app', async () => {
      const http = setupTest();

      const application: any = { attributes: { cloudProviders: [] } };
      delete SETTINGS.defaultProviders;
      const result = await flush(http, AccountService.listProviders(application));
      expect(result).toEqual(['aws', 'cf', 'gce']);
    });
  });
});
