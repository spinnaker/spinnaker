import { registerDefaultFixtures } from '../../support/defaultFixtures';

describe('core: Application executions', () => {
  beforeEach(() => registerDefaultFixtures());

  it('renders the application executions route', () => {
    cy.visit('#/applications/compute/executions');
    cy.get('.application-name').contains('compute');
    cy.get('.nav-category.active').contains('Pipelines');
    cy.contains('No pipelines configured for this application.');
  });
});
