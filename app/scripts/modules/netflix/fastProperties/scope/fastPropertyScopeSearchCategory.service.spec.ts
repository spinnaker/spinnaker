import {mock} from 'angular';

import {FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE, FastPropertyScopeCategoryService} from './fastPropertyScopeSearchCategory.service';

describe('FastPropertyScopeSearchCategory Service', function () {

  let service: FastPropertyScopeCategoryService;

  beforeEach((mock.module(FAST_PROPERTY_SCOPE_SEARCH_CATEGORY_SERVICE)));

  beforeEach(
    mock.inject(
      function ( _fastPropertyScopeSearchCategoryService_: FastPropertyScopeCategoryService) {
        service = _fastPropertyScopeSearchCategoryService_;
      }));


  let testMatch = (env: string, account: string, result: boolean) => {
    expect(service.scopeEnvMatchesAccount(env, account)).toBe(result);
  };

  describe('scopeEnvMatchesAccount', () => {

    it('should match if env = test and account = test', () => {
      testMatch('test', 'test', true);
    });

    it('should match if env = prod and account = prod', () => {
      testMatch('prod', 'prod', true);
    });

    it('should NOT match if env = test and account = prod', () => {
      testMatch('test', 'prod', false);
    });

    it('should match if env = test and account contains the word test', () => {
      testMatch('test', 'persistancetest', true);
      testMatch('test', 'testaccount', true);
    });

    it('should match if env = prod and account does not contain the word test', () => {
      testMatch('prod', 'mce', true);
      testMatch('prod', 'persistence', true);
      testMatch('prod', 'seg', true);
    });

    it('should not match if env = test and account does NOT contain the word test', () => {
      testMatch('test', 'mce', false);
      testMatch('test', 'persistence', false);
      testMatch('test', 'seg', false);
    });


  });

});
