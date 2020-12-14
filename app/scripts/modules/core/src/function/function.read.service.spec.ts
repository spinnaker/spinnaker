import { mockHttpClient } from 'core/api/mock/jasmine';
import { API, FunctionReader, IFunctionSourceData } from 'core';
import { mock } from 'angular';
import { IFunctionTransformer } from 'core/function/function.transformer';

describe('FunctionReadService', () => {
  let $httpBackend: ng.IHttpBackendService;
  const functionTransformerMock: IFunctionTransformer = {
    normalizeFunction: (data): IFunctionSourceData => {
      return data;
    },
    normalizeFunctionSet: (data): IFunctionSourceData[] => {
      return data;
    },
  };

  beforeEach(
    mock.inject(function (_$httpBackend_: ng.IHttpBackendService) {
      $httpBackend = _$httpBackend_;
    }),
  );

  describe('loadFunctions', () => {
    it(`should set cloudprovider if not set`, async (done) => {
      const http = mockHttpClient();
      const functionReader = new FunctionReader(functionTransformerMock);

      http.expectGET(`${API.baseUrl}/applications/app1/functions`).respond(200, [
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

      await http.flush();
    });

    it(`should not set cloudprovider if provided`, async (done) => {
      const http = mockHttpClient();
      const functionReader = new FunctionReader(functionTransformerMock);

      http.expectGET(`${API.baseUrl}/applications/app1/functions`).respond(200, [
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

      await http.flush();
    });
  });

  describe('getFunctionDetails', () => {
    it(`should set cloudprovider if not set`, async (done) => {
      const http = mockHttpClient();
      const functionReader = new FunctionReader(functionTransformerMock);

      http.expectGET(/.*\/functions\?.*/).respond((_method, _url, _data, _headers, params) => {
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

      await http.flush();
    });

    it(`should not set cloudprovider if provided`, async (done) => {
      const http = mockHttpClient();
      const functionReader = new FunctionReader(functionTransformerMock);

      http.expectGET(/.*\/functions\?.*/).respond((_method, _url, _data, _headers, params) => {
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

      await http.flush();
    });
  });
});
