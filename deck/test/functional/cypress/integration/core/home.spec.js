import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Home page', () => {
  beforeEach(() => registerDefaultFixtures());

  it('shows a search input field on load', function () {
    cy.visit('');
    cy.get('[data-purpose="search-v1-input"]').should(
      'have.attr',
      'placeholder',
      'projects, applications, clusters, load balancers, server groups, firewalls',
    );
  });
});
