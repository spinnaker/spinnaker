import { mockHttpClient } from '../../api/mock/jasmine';
import type { IAccountDefinition } from './AccountManagementService';
import { AccountManagementService } from './AccountManagementService';

const ACCOUNT: IAccountDefinition = {
  type: 'kubernetes',
  name: 'prod-cluster',
  kubeconfigFile: 'encrypted:secrets-manager!...',
};

describe('AccountManagementService', () => {
  describe('getAccountsByType()', () => {
    it('GETs /credentials/type/{accountType}', async () => {
      const http = mockHttpClient();
      http.expectGET(/\/credentials\/type\/kubernetes$/).respond(200, [ACCOUNT]);

      let result: IAccountDefinition[] | undefined;
      AccountManagementService.getAccountsByType('kubernetes').then((r) => (result = r));

      await http.flush();
      expect(result).toEqual([ACCOUNT]);
    });

    it('passes pagination parameters when provided', async () => {
      const http = mockHttpClient();
      http
        .expectGET(/\/credentials\/type\/kubernetes$/)
        .withParams({ limit: 100, startingAccountName: 'prod-cluster' }, true)
        .respond(200, []);

      let result: IAccountDefinition[] | undefined;
      AccountManagementService.getAccountsByType('kubernetes', 100, 'prod-cluster').then((r) => (result = r));

      await http.flush();
      expect(result).toEqual([]);
    });
  });

  describe('createAccount()', () => {
    it('POSTs /credentials with the definition body', async () => {
      const http = mockHttpClient();
      http.expectPOST(/\/credentials$/, ACCOUNT).respond(200, ACCOUNT);

      let result: IAccountDefinition | undefined;
      AccountManagementService.createAccount(ACCOUNT).then((r) => (result = r));

      await http.flush();
      expect(result).toEqual(ACCOUNT);
    });
  });

  describe('updateAccount()', () => {
    it('PUTs /credentials with the definition body', async () => {
      const http = mockHttpClient();
      http.expectPUT(/\/credentials$/, ACCOUNT).respond(200, ACCOUNT);

      let result: IAccountDefinition | undefined;
      AccountManagementService.updateAccount(ACCOUNT).then((r) => (result = r));

      await http.flush();
      expect(result).toEqual(ACCOUNT);
    });
  });

  describe('deleteAccount()', () => {
    it('DELETEs /credentials/{accountName}', async () => {
      const http = mockHttpClient();
      http.expectDELETE(/\/credentials\/prod-cluster$/).respond(204, null);

      let resolved = false;
      AccountManagementService.deleteAccount('prod-cluster').then(() => (resolved = true));

      await http.flush();
      expect(resolved).toBe(true);
    });
  });
});
