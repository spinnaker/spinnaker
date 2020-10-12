import { API, FunctionReader, IFunctionSourceData } from 'core';
import { mock } from 'angular';
import { IFunctionTransformer } from 'core/function/function.transformer';

describe('FunctionReadService', () => {
  let $http: ng.IHttpBackendService;
  const functionTransformerMock: IFunctionTransformer = {
    normalizeFunction: (data): IFunctionSourceData => {
      return data;
    },
    normalizeFunctionSet: (data): IFunctionSourceData[] => {
      return data;
    },
  };

  beforeEach(
    mock.inject(function ($httpBackend: ng.IHttpBackendService) {
      $http = $httpBackend;
    }),
  );

  describe('loadFunctions', () => {
    it(`should set cloudprovider if not set`, (done) => {
      const functionReader = new FunctionReader(functionTransformerMock);

      $http.expectGET(`${API.baseUrl}/applications/app1/functions`).respond(200, [
        {
          name: 'account1',
          provider: 'aws',
          type: 'aws',
        },
        {
          name: 'account1',
          provider: 'aws',
          type: 'aws',
        },
      ]);

      functionReader.loadFunctions('app1').then((data) => {
        expect(data).toEqual([
          {
            name: 'account1',
            cloudProvider: 'aws',
            provider: 'aws',
            type: 'aws',
          },
          {
            name: 'account1',
            cloudProvider: 'aws',
            provider: 'aws',
            type: 'aws',
          },
        ]);

        done();
      });

      $http.flush();
    });

    it(`should not set cloudprovider if provided`, (done) => {
      const functionReader = new FunctionReader(functionTransformerMock);

      $http.expectGET(`${API.baseUrl}/applications/app1/functions`).respond(200, [
        {
          name: 'account1',
          cloudProvider: 'fluffyCloud',
          provider: 'aws',
          type: 'aws',
        },
        {
          name: 'account1',
          cloudProvider: 'fluffyCloud',
          provider: 'aws',
          type: 'aws',
        },
      ]);

      functionReader.loadFunctions('app1').then((data) => {
        expect(data).toEqual([
          {
            name: 'account1',
            cloudProvider: 'fluffyCloud',
            provider: 'aws',
            type: 'aws',
          },
          {
            name: 'account1',
            cloudProvider: 'fluffyCloud',
            provider: 'aws',
            type: 'aws',
          },
        ]);

        done();
      });

      $http.flush();
    });
  });

  describe('getFunctionDetails', () => {
    it(`should set cloudprovider if not set`, (done) => {
      const functionReader = new FunctionReader(functionTransformerMock);

      $http.whenGET(/.*\/functions\?.*/).respond((_method, _url, _data, _headers, params) => {
        expect(params).toEqual({
          provider: 'aws',
          account: 'acct1',
          region: 'us-west-1',
          functionName: 'runner1',
        });

        return [
          200,
          [
            {
              name: 'account1',
              provider: 'aws',
              type: 'aws',
            },
            {
              name: 'account1',
              provider: 'aws',
              type: 'aws',
            },
          ],
        ];
      });

      functionReader.getFunctionDetails('aws', 'acct1', 'us-west-1', 'runner1').then((data) => {
        expect(data).toEqual([
          {
            name: 'account1',
            cloudProvider: 'aws',
            provider: 'aws',
            type: 'aws',
          },
          {
            name: 'account1',
            cloudProvider: 'aws',
            provider: 'aws',
            type: 'aws',
          },
        ]);

        done();
      });

      $http.flush();
    });

    it(`should not set cloudprovider if provided`, (done) => {
      const functionReader = new FunctionReader(functionTransformerMock);

      $http.whenGET(/.*\/functions\?.*/).respond((_method, _url, _data, _headers, params) => {
        expect(params).toEqual({
          provider: 'aws',
          account: 'acct1',
          region: 'us-west-1',
          functionName: 'runner1',
        });

        return [
          200,
          [
            {
              name: 'account1',
              cloudProvider: 'fluffyCloud',
              provider: 'aws',
              type: 'aws',
            },
            {
              name: 'account1',
              cloudProvider: 'fluffyCloud',
              provider: 'aws',
              type: 'aws',
            },
          ],
        ];
      });

      functionReader.getFunctionDetails('aws', 'acct1', 'us-west-1', 'runner1').then((data) => {
        expect(data).toEqual([
          {
            name: 'account1',
            cloudProvider: 'fluffyCloud',
            provider: 'aws',
            type: 'aws',
          },
          {
            name: 'account1',
            cloudProvider: 'fluffyCloud',
            provider: 'aws',
            type: 'aws',
          },
        ]);

        done();
      });

      $http.flush();
    });
  });
});
