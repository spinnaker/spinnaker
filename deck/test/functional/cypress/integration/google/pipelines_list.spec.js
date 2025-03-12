import { registerDefaultFixtures } from '../../support';

describe('google: Compute Pipelines', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.route('/applications/compute/pipelines?expand=false&limit=2', 'fixture:google/pipelines_list/pipelines.json');
    cy.route('/applications/compute/pipelineConfigs', 'fixture:google/pipelines_list/pipelineConfigs.json');
  });

  it('shows stored GCE pipelines with their account tag', () => {
    cy.visit('#/applications/compute/executions');
    cy.get('.execution-group').should('have.length', 9);
    cy.get('.account-tag')
      .first()
      .should('contain.text', 'gce');
  });
});
