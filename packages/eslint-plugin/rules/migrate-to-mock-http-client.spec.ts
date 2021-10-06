import rule from './migrate-to-mock-http-client';
import ruleTester from '../utils/ruleTester';

ruleTester.run('migrate-to-mock-http-client', rule, {
  valid: [
    {
      code: 'it(() => { const http = mockHttpClient(); })',
    },
  ],
  invalid: [
    {
      code: `it('does things', () => { $httpBackend.flush() })`,
      output: `it('does things', async () => { $httpBackend.flush() })`,
      errors: ['Migrate to MockHttpClient (step 1): make test function async'],
    },

    // Step 1 make async
    {
      code: `
describe('foo bar', () => {
  it('does things', () => {
    $httpBackend.flush()
  })
})`,
      output: `
describe('foo bar', () => {
  it('does things', async () => {
    $httpBackend.flush()
  })
})`,
      errors: ['Migrate to MockHttpClient (step 1): make test function async'],
    },

    // Step 2 create mock
    {
      code: `
describe('foo bar', () => {
  it('does things', async () => {
    $httpBackend.flush()
  })
})`,
      output: `import { mockHttpClient } from 'core/api/mock/jasmine';

describe('foo bar', () => {
  it('does things', async () => {
    const http = mockHttpClient();
$httpBackend.flush()
  })
})`,
      errors: ['Migrate to MockHttpClient (step 2): Create a MockHttpClient named "http"'],
    },

    // Step 3 change variables
    {
      code: `
import { mockHttpClient } from 'core/api/mock/jasmine';
describe('foo bar', () => {
  it('does things', async () => {
    const http = mockHttpClient();
    $httpBackend.expectGET('/foo/bar').respond(200, { bar: 15 });
    service.fetchBars();
    $httpBackend.flush()
  })
})`,
      output: `
import { mockHttpClient } from 'core/api/mock/jasmine';
describe('foo bar', () => {
  it('does things', async () => {
    const http = mockHttpClient();
    http.expectGET('/foo/bar').respond(200, { bar: 15 });
    service.fetchBars();
    await http.flush()
  })
})`,
      errors: ['Migrate to MockHttpClient (step 3): replace $httpBackend with http'],
    },
  ],
});
