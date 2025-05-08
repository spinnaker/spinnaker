import { registerDefaultFixtures } from '../../support';

describe('amazon ecs: ECSApp Pipeline', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications/ecsapp/pipelines?expand=false', { fixture: 'ecs/pipelines/pipelines.json' });
    cy.intercept('/applications/ecsapp/pipelines?expand=false&limit=2', { fixture: 'ecs/pipelines/pipelines.json' });
    cy.intercept('/images/find?*', { fixture: 'google/shared/images.json' });
    cy.intercept('/applications/ecsapp/pipelineConfigs', { fixture: 'ecs/pipelines/pipelineConfigs.json' });
    cy.intercept('/networks/aws', { fixture: 'ecs/default/networks.aws-ecs.json' });
    cy.intercept('/applications/ecsapp/serverGroups', { fixture: 'ecs/clusters/serverGroups.json' });
    cy.intercept('/applications/ecsapp/serverGroups/**/aws-prod-ecsdemo-v000?includeDetails=false', {
      fixture: 'ecs/clusters/serverGroup.ecsdemo-v000.json',
    });
  });

  it('shows stored ECSApp pipelines with their account tag', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('.execution-group').should('have.length', 1);

    cy.get('.account-tag').first().should('contain.text', 'ecs');
  });

  it('configures stored ECSApp pipeline and deletes server group', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('a:contains("Configure")').click({ force: true });

    cy.get('a:contains("Deploy")').click({ force: true });

    cy.get('.glyphicon-duplicate').eq(1).click({ force: true });

    cy.get('.glyphicon-trash').eq(1).click({ force: true });

    cy.get('.account-tag').should('have.length', 1);

    cy.get('td:contains("ecsapp-prod-ecsdemo")').should('have.length', 1);

    cy.get('td:contains("us-west-2")').should('have.length', 1);

    cy.get('td:contains("redblack")').should('have.length', 1);
  });

  it('configures stored ECSApp pipeline and duplicates server group', () => {
    cy.visit('#/applications/ecsapp/executions');

    cy.get('a:contains("Configure")').click({ force: true });

    cy.get('a:contains("Deploy")').click({ force: true });

    cy.get('.glyphicon-duplicate').eq(1).click({ force: true });

    cy.get('.account-tag').should('have.length', 2);

    cy.get('.account-tag').eq(0).should('contain.text', 'ecs-my-aws-devel-acct');

    cy.get('.account-tag').eq(1).should('contain.text', 'ecs-my-aws-devel-acct');

    cy.get('td:contains("ecsapp-prod-ecsdemo")').should('have.length', 2);

    cy.get('td:contains("us-west-2")').should('have.length', 2);

    cy.get('td:contains("redblack")').should('have.length', 2);
  });
});
