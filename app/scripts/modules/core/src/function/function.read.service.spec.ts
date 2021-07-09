import { mockHttpClient } from '../api/mock/jasmine';
import { FunctionReader, IFunctionSourceData } from '../index';
import { MockHttpClient } from '../api/mock/mockHttpClient';
import { IFunctionTransformer } from './function.transformer';

function flush<T>(http: MockHttpClient, promise: PromiseLike<T>): Promise<T> {
  return http.flush().then(() => promise);
}

describe('FunctionReadService', () => {
  const functionTransformerMock: IFunctionTransformer = {
    normalizeFunction: (data): IFunctionSourceData => {
      return data;
    },
    normalizeFunctionSet: (data): IFunctionSourceData[] => {
      return data;
    },
  };

  describe('loadFunctions', () => {
    it(`should set cloudprovider if not set`, async () => {
      const http = mockHttpClient();
      const functionReader = new FunctionReader(functionTransformerMock);

      http.expectGET(`/applications/app1/functions`).respond(200, [
        { name: 'account1', provider: 'aws', type: 'aws' },
        { name: 'account1', provider: 'aws', type: 'aws' },
      ]);

      const data = await flush(http, functionReader.loadFunctions('app1'));
      expect(data).toEqual([
        { name: 'account1', cloudProvider: 'aws', provider: 'aws', type: 'aws' },
        { name: 'account1', cloudProvider: 'aws', provider: 'aws', type: 'aws' },
      ]);
    });

    it(`should not set cloudprovider if provided`, async () => {
      const http = mockHttpClient();
      const functionReader = new FunctionReader(functionTransformerMock);

      http.expectGET(`/applications/app1/functions`).respond(200, [
        { name: 'account1', cloudProvider: 'fluffyCloud', provider: 'aws', type: 'aws' },
        { name: 'account1', cloudProvider: 'fluffyCloud', provider: 'aws', type: 'aws' },
      ]);

      const data = await flush(http, functionReader.loadFunctions('app1'));
      expect(data).toEqual([
        { name: 'account1', cloudProvider: 'fluffyCloud', provider: 'aws', type: 'aws' },
        { name: 'account1', cloudProvider: 'fluffyCloud', provider: 'aws', type: 'aws' },
      ]);
    });
  });

  describe('getFunctionDetails', () => {
    it(`should set cloudprovider if not set`, async () => {
      const http = mockHttpClient();
      const functionReader = new FunctionReader(functionTransformerMock);

      http
        .expectGET(/.*\/functions/)
        .withParams({ provider: 'aws', account: 'acct1', region: 'us-west-1', functionName: 'runner1' })
        .respond(200, [
          { name: 'account1', provider: 'aws', type: 'aws' },
          { name: 'account1', provider: 'aws', type: 'aws' },
        ]);

      const data = await flush(http, functionReader.getFunctionDetails('aws', 'acct1', 'us-west-1', 'runner1'));
      expect(data).toEqual([
        { name: 'account1', cloudProvider: 'aws', provider: 'aws', type: 'aws' },
        { name: 'account1', cloudProvider: 'aws', provider: 'aws', type: 'aws' },
      ]);
    });

    it(`should not set cloudprovider if provided`, async () => {
      const http = mockHttpClient();
      const functionReader = new FunctionReader(functionTransformerMock);

      http
        .expectGET(/.*\/functions/)
        .withParams({ provider: 'aws', account: 'acct1', region: 'us-west-1', functionName: 'runner1' })
        .respond(200, [
          { name: 'account1', cloudProvider: 'fluffyCloud', provider: 'aws', type: 'aws' },
          { name: 'account1', cloudProvider: 'fluffyCloud', provider: 'aws', type: 'aws' },
        ]);

      const data = await flush(http, functionReader.getFunctionDetails('aws', 'acct1', 'us-west-1', 'runner1'));
      expect(data).toEqual([
        { name: 'account1', cloudProvider: 'fluffyCloud', provider: 'aws', type: 'aws' },
        { name: 'account1', cloudProvider: 'fluffyCloud', provider: 'aws', type: 'aws' },
      ]);
    });
  });
});
