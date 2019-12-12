'use strict';

import { module } from 'angular';

import { chain, flow } from 'lodash';

import { AccountService } from 'core/account/AccountService';
import { PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';

export const CORE_FUNCTION_FUNCTION_TRANSFORMER = 'spinnaker.core.function.transformer';
export const name = CORE_FUNCTION_FUNCTION_TRANSFORMER; // for backwards compatibility
module(CORE_FUNCTION_FUNCTION_TRANSFORMER, [PROVIDER_SERVICE_DELEGATE]).factory('functionTransformer', [
  'providerServiceDelegate',
  function(providerServiceDelegate) {
    function normalizeFunction(functionDef) {
      return AccountService.getAccountDetails(functionDef.account).then(accountDetails => {
        return providerServiceDelegate
          .getDelegate(
            functionDef.provider ? functionDef.provider : 'aws',
            'function.transformer',
            accountDetails && accountDetails.skin,
          )
          .normalizeFunction(functionDef);
      });
    }

    function normalizeFunctionSet(functions) {
      const setNormalizers = chain(functions)
        .filter(fn => providerServiceDelegate.hasDelegate(fn.provider ? fn.provider : 'aws', 'function.setTransformer'))
        .compact()
        .map(fn => providerServiceDelegate.getDelegate(fn.provider, 'function.setTransformer').normalizeFunctionSet)
        .uniq()
        .value();

      if (setNormalizers.length) {
        return flow(setNormalizers)(functions);
      } else {
        return functions;
      }
    }

    return {
      normalizeFunction: normalizeFunction,
      normalizeFunctionSet: normalizeFunctionSet,
    };
  },
]);
