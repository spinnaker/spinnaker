import { mock } from 'angular';
import { API } from 'core/api/ApiService';
import { ACCOUNT_SERVICE, AccountService, IAccount } from 'core/account/account.service';
import { $rootScope } from 'ngimport';
import { CloudProviderRegistry } from '../cloudProvider';
import { SETTINGS } from 'core/config/settings';

describe('Service: accountService', () => {
  let $http: ng.IHttpBackendService;
  let accountService: AccountService;

  beforeEach(mock.module(ACCOUNT_SERVICE));

  beforeEach(
    mock.inject(function($httpBackend: ng.IHttpBackendService, _accountService_: AccountService) {
      $http = $httpBackend;
      accountService = _accountService_;
    }),
  );

  afterEach(SETTINGS.resetToOriginal);

  it('should filter the list of accounts by provider when supplied', done => {
    $http
      .expectGET(`${API.baseUrl}/credentials?expand=true`)
      .respond(200, [
        { name: 'test', type: 'aws' },
        { name: 'prod', type: 'aws' },
        { name: 'prod', type: 'gce' },
        { name: 'gce-test', type: 'gce' },
      ]);

    accountService.listAccounts('aws').then((accounts: IAccount[]) => {
      expect(accounts.length).toBe(2);
      expect(accounts.map((account: IAccount) => account.name)).toEqual(['test', 'prod']);
      done();
    });

    $http.flush();
    setTimeout(() => $rootScope.$digest());
  });

  describe('getAllAccountDetailsForProvider', () => {
    it('should return details for each account', done => {
      $http
        .expectGET(API.baseUrl + '/credentials?expand=true')
        .respond(200, [{ name: 'test', type: 'aws' }, { name: 'prod', type: 'aws' }]);

      accountService.getAllAccountDetailsForProvider('aws').then((details: any) => {
        expect(details.length).toBe(2);
        expect(details[0].name).toBe('test');
        expect(details[1].name).toBe('prod');
        done();
      });

      $http.flush();
      setTimeout(() => $rootScope.$digest());
    });

    it('should fall back to an empty array if an exception occurs when listing accounts', done => {
      $http.expectGET(`${API.baseUrl}/credentials?expand=true`).respond(429, null);

      accountService.getAllAccountDetailsForProvider('aws').then((details: any[]) => {
        expect(details).toEqual([]);
        done();
      });

      $http.flush();
      setTimeout(() => $rootScope.$digest());
    });
  });

  describe('listProviders', () => {
    let registeredProviders: string[];
    beforeEach(() => {
      registeredProviders = ['aws', 'gce', 'cf'];
      $http
        .whenGET(`${API.baseUrl}/credentials?expand=true`)
        .respond(200, [{ type: 'aws' }, { type: 'gce' }, { type: 'cf' }]);

      spyOn(CloudProviderRegistry, 'listRegisteredProviders').and.returnValue(registeredProviders);
    });

    it('should list all providers when no application provided', () => {
      const test: any = (result: string[]) => expect(result).toEqual(['aws', 'cf', 'gce']);
      accountService.listProviders().then(test);
      $http.flush();
    });

    it('should filter out providers not registered', () => {
      registeredProviders.pop();
      const test: any = (result: string[]) => expect(result).toEqual(['aws', 'gce']);
      accountService.listProviders().then(test);
      $http.flush();
    });

    it('should fall back to the defaultProviders if none configured for the application', () => {
      const application: any = { attributes: { cloudProviders: [] } };
      const test: any = (result: string[]) => expect(result).toEqual(['cf', 'gce']);
      SETTINGS.defaultProviders = ['gce', 'cf'];
      accountService.listProviders(application).then(test);
      $http.flush();
    });

    it('should return the intersection of those configured for the application and those available from the server', () => {
      const application: any = { attributes: { cloudProviders: ['gce', 'cf', 'unicron'] } };
      const test: any = (result: string[]) => expect(result).toEqual(['cf', 'gce']);
      SETTINGS.defaultProviders = ['aws'];
      accountService.listProviders(application).then(test);
      $http.flush();
    });

    it('should return an empty array if none of the app providers are available from the server', () => {
      const application: any = { attributes: { cloudProviders: ['lamp', 'ceiling', 'fan'] } };
      const test: any = (result: string[]) => expect(result).toEqual([]);
      SETTINGS.defaultProviders = ['foo'];
      accountService.listProviders(application).then(test);
      $http.flush();
    });

    it('should fall back to all registered available providers if no defaults configured and none configured on app', () => {
      const application: any = { attributes: { cloudProviders: [] } };
      const test: any = (result: string[]) => expect(result).toEqual(['aws', 'cf', 'gce']);
      delete SETTINGS.defaultProviders;
      accountService.listProviders(application).then(test);
      $http.flush();
    });
  });
});
