import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Create Application', () => {
  beforeEach(() => registerDefaultFixtures());

  it(`shows a config screen with required fields preventing creation until they're populated`, () => {
    cy.visit('#/applications?create=testapp');

    cy.get('[data-purpose="application-name"]').should('have.value', 'testapp');
    cy.get('[data-purpose="create-application"]').should('be.disabled');
    cy.get('[data-purpose="application-email"]').type('user@testcompany.com');
    cy.get('[data-purpose="create-application"]').should('be.enabled');
  });

  it('takes the user to their application infrastructure on success', () => {
    cy.intercept('POST', '/tasks', { ref: '/tasks/01D6G3SSZ54TVWM5CA4ZC8MDXH' });
    cy.intercept('/tasks/01D6G3SSZ54TVWM5CA4ZC8MDXH', {
      fixture: 'core/create_application/create_testapp1.success.json',
    });
    cy.intercept('/applications/testapp1?expand=false', {
      fixture: 'core/create_application/testapp1.json',
    });

    cy.visit('#/applications?create=testapp1');

    cy.get('[data-purpose="application-email"]').type('user@testcompany.com');
    cy.get('[data-purpose="create-application"]').click();
    cy.url().should('include', '#/applications/testapp1/clusters');
  });
});
