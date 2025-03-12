import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Create Application', () => {
  beforeEach(() => registerDefaultFixtures());
  it(`shows a config screen with required fields preventing creation until they're populated`, () => {
    cy.visit('#/applications');
    cy.get('a:contains("Create Application")').click();

    cy.get('button:contains("Create")').should('be.disabled');
    cy.get('input[name=name]').type('testapp');
    cy.get('button:contains("Create")').should('be.disabled');
    cy.get('input[name=email]').type('user@testcompany.com');
    cy.get('button:contains("Create")').should('be.enabled');
  });

  it('takes the user to their application infrastructure on success', () => {
    cy.route('POST', '/tasks', { ref: '/tasks/01D6G3SSZ54TVWM5CA4ZC8MDXH' });
    cy.route('/tasks/01D6G3SSZ54TVWM5CA4ZC8MDXH', 'fixture:core/create_application/create_testapp1.success.json');
    cy.route('/applications/testapp1?expand=false', 'fixture:core/create_application/testapp1.json');

    cy.visit('#/applications');
    cy.get('a:contains("Create Application")').click();

    cy.get('input[name=name]').type('testapp1');
    cy.get('input[name=email]').type('user@testcompany.com');
    cy.get('button:contains("Create")').click();
    cy.url().should('include', '#/applications/testapp1/clusters');
  });
});
