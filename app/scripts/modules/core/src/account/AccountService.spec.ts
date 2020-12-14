import { mockHttpClient } from 'core/api/mock/jasmine';
import { mock } from 'angular';
import { $rootScope } from 'ngimport';

import { API } from 'core/api/ApiService';
import { SETTINGS } from 'core/config/settings';

import { AccountService, IAccount } from './AccountService';
import { CloudProviderRegistry } from '../cloudProvider';

describe('Service: AccountService', () => {
  let $httpBackend: ng.IHttpBackendService;

  beforeEach(
    mock.inject(function (_$httpBackend_: ng.IHttpBackendService) {
      $httpBackend = _$httpBackend_;
    }),
  );
  beforeEach(() => AccountService.initialize());

  afterEach(SETTINGS.resetToOriginal);

  it('should filter the list of accounts by provider when supplied', async (done) => {
    const http = mockHttpClient();
    http.expectGET(`${API.baseUrl}/credentials?expand=true`).respond(200, [
      { name: 'test', type: 'aws' },
      { name: 'prod', type: 'aws' },
      { name: 'prod', type: 'gce' },
      { name: 'gce-test', type: 'gce' },
    ]);

    AccountService.listAccounts('aws').then((accounts: IAccount[]) => {
      expect(accounts.length).toBe(2);
      expect(accounts.map((account: IAccount) => account.name)).toEqual(['test', 'prod']);
      done();
    });
    await http.flush();
    setTimeout(() => $rootScope.$digest());
  });

  describe('getAllAccountDetailsForProvider', () => {
    it('should return details for each account', async (done) => {
      const http = mockHttpClient();
      http.expectGET(API.baseUrl + '/credentials?expand=true').respond(200, [
        { name: 'test', type: 'aws' },
        { name: 'prod', type: 'aws' },
      ]);

      AccountService.getAllAccountDetailsForProvider('aws').then((details: any) => {
        expect(details.length).toBe(2);
        expect(details[0].name).toBe('test');
        expect(details[1].name).toBe('prod');
        done();
      });
      await http.flush();
      setTimeout(() => $rootScope.$digest());
    });

    it('should fall back to an empty array if an exception occurs when listing accounts', async (done) => {
      const http = mockHttpClient();
      http.expectGET(`${API.baseUrl}/credentials?expand=true`).respond(429, null);

      AccountService.getAllAccountDetailsForProvider('aws').then((details: any[]) => {
        expect(details).toEqual([]);
        done();
      });
      await http.flush();
      setTimeout(() => $rootScope.$digest());
    });
  });

  describe('listProviders', () => {
    let registeredProviders: string[];
    beforeEach(() => {
      registeredProviders = ['aws', 'gce', 'cf'];
      $httpBackend
        .whenGET(`${API.baseUrl}/credentials?expand=true`)
        .respond(200, [{ type: 'aws' }, { type: 'gce' }, { type: 'cf' }]);
      spyOn(CloudProviderRegistry, 'listRegisteredProviders').and.returnValue(registeredProviders);
    });

    it('should list all providers when no application provided', async (done) => {
      const http = mockHttpClient();
      AccountService.listProviders().then((result: string[]) => {
        expect(result).toEqual(['aws', 'cf', 'gce']);
        done();
      });
      await http.flush();
      setTimeout(() => $rootScope.$digest());
    });

    it('should filter out providers not registered', async (done) => {
      const http = mockHttpClient();
      registeredProviders.pop();
      AccountService.listProviders().then((result: string[]) => {
        expect(result).toEqual(['aws', 'gce']);
        done();
      });
      await http.flush();
      setTimeout(() => $rootScope.$digest());
    });

    it('should fall back to the defaultProviders if none configured for the application', async (done) => {
      const http = mockHttpClient();
      const application: any = { attributes: { cloudProviders: [] } };
      SETTINGS.defaultProviders = ['gce', 'cf'];
      AccountService.listProviders(application).then((result: string[]) => {
        expect(result).toEqual(['cf', 'gce']);
        done();
      });
      await http.flush();
      setTimeout(() => $rootScope.$digest());
    });

    it('should return the intersection of those configured for the application and those available from the server', async (done) => {
      const http = mockHttpClient();
      const application: any = { attributes: { cloudProviders: ['gce', 'cf', 'unicron'] } };
      SETTINGS.defaultProviders = ['aws'];
      AccountService.listProviders(application).then((result: string[]) => {
        expect(result).toEqual(['cf', 'gce']);
        done();
      });
      await http.flush();
      setTimeout(() => $rootScope.$digest());
    });

    it('should return an empty array if none of the app providers are available from the server', async (done) => {
      const http = mockHttpClient();
      const application: any = { attributes: { cloudProviders: ['lamp', 'ceiling', 'fan'] } };
      SETTINGS.defaultProviders = ['foo'];
      AccountService.listProviders(application).then((result: string[]) => {
        expect(result).toEqual([]);
        done();
      });
      await http.flush();
      setTimeout(() => $rootScope.$digest());
    });

    it('should fall back to all registered available providers if no defaults configured and none configured on app', async (done) => {
      const http = mockHttpClient();
      const application: any = { attributes: { cloudProviders: [] } };
      delete SETTINGS.defaultProviders;
      AccountService.listProviders(application).then((result: string[]) => {
        expect(result).toEqual(['aws', 'cf', 'gce']);
        done();
      });
      await http.flush();
      setTimeout(() => $rootScope.$digest());
    });
  });
});
