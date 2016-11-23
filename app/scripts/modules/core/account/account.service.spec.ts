import {API_SERVICE, Api} from 'core/api/api.service';
import {ACCOUNT_SERVICE, AccountService, IAccount} from 'core/account/account.service';

describe('Service: accountService', () => {

  let $http: ng.IHttpBackendService;
  let settings: any;
  let cloudProviderRegistry: any;
  let API: Api;
  let accountService: AccountService;

  beforeEach((angular.mock.module(API_SERVICE, ACCOUNT_SERVICE)));

  beforeEach(
    angular.mock.inject(
      function ($httpBackend: ng.IHttpBackendService,
                _settings_: any,
                _cloudProviderRegistry_: any,
                _API_: Api,
                _accountService_: AccountService) {
        $http = $httpBackend;
        settings = _settings_;
        cloudProviderRegistry = _cloudProviderRegistry_;
        API = _API_;
        accountService = _accountService_;
      }));

  it('should filter the list of accounts by provider when supplied', () => {
    $http.expectGET(`${API.baseUrl}/credentials`).respond(200, [
      {name: 'test', type: 'aws'},
      {name: 'prod', type: 'aws'},
      {name: 'prod', type: 'gce'},
      {name: 'gce-test', type: 'gce'},
    ]);

    let accounts: IAccount[] = null;
    accountService.listAccounts('aws').then((results: IAccount[]) => accounts = results);
    $http.flush();

    expect(accounts.length).toBe(2);
    expect(accounts.map((account: IAccount) => account.name)).toEqual(['test', 'prod']);
  });

  describe('getAllAccountDetailsForProvider', () => {

    it('should return details for each account', function () {
      $http.expectGET(API.baseUrl + '/credentials').respond(200, [
        {name: 'test', type: 'aws'},
        {name: 'prod', type: 'aws'},
      ]);

      $http.expectGET(API.baseUrl + '/credentials/test').respond(200, {a: 1});
      $http.expectGET(API.baseUrl + '/credentials/prod').respond(200, {a: 2});

      let details: any = null;
      accountService.getAllAccountDetailsForProvider('aws').then((results: any) => {
        details = results;
      });

      $http.flush();
      expect(details.length).toBe(2);
      expect(details[0].a).toBe(1);
      expect(details[1].a).toBe(2);
    });

    it('should fall back to an empty array if an exception occurs when listing accounts', () => {
      $http.expectGET(`${API.baseUrl}/credentials`).respond(429, null);

      let details: any[] = null;
      accountService.getAllAccountDetailsForProvider('aws').then((results: any[]) => {
        details = results;
      });

      $http.flush();
      expect(details).toEqual([]);
    });

    it('should fall back to an empty array if an exception occurs when getting details for an account', () => {
      $http.expectGET(`${API.baseUrl}/credentials`).respond(200, [
        {name: 'test', type: 'aws'},
        {name: 'prod', type: 'aws'},
      ]);

      $http.expectGET(API.baseUrl + '/credentials/test').respond(500, null);
      $http.expectGET(API.baseUrl + '/credentials/prod').respond(200, {a: 2});

      let details: any = null;
      accountService.getAllAccountDetailsForProvider('aws').then((results: any) => {
        details = results;
      });

      $http.flush();

      expect(details).toEqual([]);
    });
  });

  describe('listProviders', () => {

    let registeredProviders: string[];
    beforeEach(() => {
      registeredProviders = ['aws', 'gce', 'cf'];
      $http.whenGET(`${API.baseUrl}/credentials`).respond(200,
        [{type: 'aws'}, {type: 'gce'}, {type: 'cf'}]
      );

      spyOn(cloudProviderRegistry, 'listRegisteredProviders').and.returnValue(registeredProviders);
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

      const application: any = {attributes: { cloudProviders: [] }};
      const test: any = (result: string[]) => expect(result).toEqual(['cf', 'gce']);
      settings.defaultProviders = ['gce', 'cf'];
      accountService.listProviders(application).then(test);
      $http.flush();
    });

    it('should return the intersection of those configured for the application and those available from the server', () => {

      const application: any = {attributes: {cloudProviders: ['gce', 'cf', 'unicron']}};
      const test: any = (result: string[]) => expect(result).toEqual(['cf', 'gce']);
      settings.defaultProviders = ['aws'];
      accountService.listProviders(application).then(test);
      $http.flush();
    });

    it('should return an empty array if none of the app providers are available from the server', () => {

      const application: any = {attributes: {cloudProviders: ['lamp', 'ceiling', 'fan']}};
      const test: any = (result: string[]) => expect(result).toEqual([]);
      settings.defaultProviders = 'aws';
      accountService.listProviders(application).then(test);
      $http.flush();
    });

    it('should fall back to all registered available providers if no defaults configured and none configured on app', () => {

      const application: any = {attributes: { cloudProviders: [] }};
      const test: any = (result: string[]) => expect(result).toEqual(['aws', 'cf', 'gce']);
      delete settings.defaultProviders;
      accountService.listProviders(application).then(test);
      $http.flush();
    });
  });
});
