import { registerDefaultFixtures } from '../../support';

describe('core: bootstrap', () => {
  beforeEach(() => {
    registerDefaultFixtures();
  });

  it('uses the React root instead of AngularJS document bootstrap', () => {
    cy.visit('/');

    cy.get('#spinnaker-root').should('exist');
    cy.get('html').should('not.have.attr', 'ng-app');
    cy.get('html').should('not.have.attr', 'ng-strict-di');
    cy.get('spinnaker').should('not.exist');
  });
});
