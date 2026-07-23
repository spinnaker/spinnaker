import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Search V1', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept(
      {
        pathname: '/search',
        query: { pageSize: '500', q: 'compute', type: 'applications' },
      },
      {
        body: [
          {
            results: [
              {
                accounts: ['my-google-account'],
                application: 'compute',
                email: 'owner@example.com',
                provider: 'gce',
                type: 'applications',
              },
            ],
          },
        ],
      },
    ).as('applicationSearch');
  });

  it('requires three characters and renders direct infrastructure results', () => {
    cy.visit('#/search');

    cy.get('[data-purpose="search-v1-input"]').type('co');
    cy.get('[data-purpose="search-v1-min-length"]').should('be.visible');

    cy.get('[data-purpose="search-v1-input"]').type('mpute');
    cy.wait('@applicationSearch');
    cy.get('.category-container').should('contain.text', 'Applications (1)');
    cy.get('.category-container a')
      .should('contain.text', 'compute')
      .and('have.attr', 'href')
      .and('include', 'compute');
    cy.url().should('include', 'q=compute');
  });
});
