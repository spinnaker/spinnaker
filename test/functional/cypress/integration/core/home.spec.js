import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Home page', () => {
  beforeEach(() => registerDefaultFixtures());

  it('shows a search input field on load', function() {
    cy.visit('');
    cy.get('.header-section input[type="search"]');
  });
});
