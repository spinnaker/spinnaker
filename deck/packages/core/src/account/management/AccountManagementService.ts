import { REST } from '../../api/ApiService';

/**
 * A Clouddriver account definition as managed by the account management APIs
 * (Gate's /credentials endpoints). Beyond the `type` discriminator and unique
 * `name`, the shape is provider-specific and treated as an open JSON document.
 */
export interface IAccountDefinition {
  type: string;
  name: string;
  [attribute: string]: any;
}

export const AccountManagementService = {
  /** Lists account definitions of the given type. Both parameters are optional pagination controls. */
  getAccountsByType(
    accountType: string,
    limit?: number,
    startingAccountName?: string,
  ): PromiseLike<IAccountDefinition[]> {
    const params: Record<string, string | number> = {};
    if (limit != null) {
      params.limit = limit;
    }
    if (startingAccountName) {
      params.startingAccountName = startingAccountName;
    }
    return REST('/credentials/type').path(accountType).query(params).get();
  },

  createAccount(accountDefinition: IAccountDefinition): PromiseLike<IAccountDefinition> {
    return REST('/credentials').post(accountDefinition);
  },

  updateAccount(accountDefinition: IAccountDefinition): PromiseLike<IAccountDefinition> {
    return REST('/credentials').put(accountDefinition);
  },

  deleteAccount(accountName: string): PromiseLike<void> {
    return REST('/credentials').path(accountName).delete();
  },
};
