import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Application List', () => {
  beforeEach(() => registerDefaultFixtures());

  it('shows a list of applications', () => {
    cy.visit('');
    cy.get('a:contains("Applications")').click();
    cy.get('a:contains("compute")');
    cy.get('a:contains("ecsapp")');
    cy.get('a:contains("gae")');
    cy.get('a:contains("gke")');
  });
});
