import { registerDefaultFixtures } from '../../support';

describe('amazon ecs: ECSApp Pipeline', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.route('/applications/ecsapp/pipelines?expand=false', 'fixture:ecs/pipelines/pipelines.json');
    cy.route('/applications/ecsapp/pipelines?expand=false&limit=2', 'fixture:ecs/pipelines/pipelines.json');
    cy.route('/applications/ecsapp/pipelineConfigs', 'fixture:ecs/pipelines/pipelineConfigs.json');
  });

  it('shows stored ECSApp pipelines with their account tag', () => {
    cy.visit('#/applications/ecsapp/executions');
    cy.get('.execution-group').should('have.length', 1);
    cy.get('.account-tag')
      .first()
      .should('contain.text', 'ecs');
  });
});
