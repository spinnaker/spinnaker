import { registerDefaultFixtures } from '../../support';

describe('kubernetes: Pipelines', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('/applications/kubernetesapp/pipelines?expand=false&limit=2', {
      fixture: 'kubernetes/pipelines/pipelines.json',
    });

    cy.intercept('/applications/kubernetesapp/pipelineConfigs', {
      fixture: 'kubernetes/pipelines/pipelineConfigs.json',
    });

    cy.intercept('/applications/kubernetesapp/pipelineConfigs/bake_and_deploy_manifest', {
      fixture: 'kubernetes/pipelines/pipelineConfigs.json',
    });

    cy.intercept('/pipelines/01JSDR9Q2VBK2PTRZWKG0F5452', {
      fixture: 'kubernetes/pipelines/01JSDR9Q2VBK2PTRZWKG0F5452.succeeded.json',
    });

    cy.intercept(
      '/artifacts/content-address/kubernetesapp/b323f5f973e9393855b3af136a9989677d2ea51e64d871f4b50d66a1a92adc7c',
      {
        fixture: 'kubernetes/pipelines/artifact.json',
      },
    );
  });

  it('displays all pipeline stages', () => {
    cy.visit('#/applications/kubernetesapp/executions');
    cy.get('.execution-details-button').contains('Execution Details').click();

    const expectedStages = [
      { name: 'Delete (Manifest)' },
      { name: 'Bake (Manifest)' },
      { name: 'Deploy (Manifest)' },
      { name: 'Rollout Restart (Manifest)' },
      { name: 'Scale (Manifest)' },
    ];

    expectedStages.forEach((stage, index) => {
      cy.get('.execution-bar .stages > span')
        .eq(index)
        .within(() => {
          cy.get('.execution-marker').should('have.class', 'execution-marker-succeeded');
        });
    });

    cy.get('.execution-summary .execution-status').should('contain.text', 'SUCCEEDED');
  });

  it('displays step-section-details tab content for Delete (Manifest)', () => {
    cy.visit('#/applications/kubernetesapp/executions');
    cy.get('.execution-details-button').contains('Execution Details').click();

    cy.get('ul.nav.nav-pills li').first().contains('Delete Manifest').should('contain.text', 'Delete Manifest');

    cy.get('.step-section-details dl').within(() => {
      cy.contains('dt', 'Account').next('dd').should('contain.text', 'k8s-local');
      cy.contains('dt', 'Manifest').next('dd').should('contain.text', 'service dev-p01-nginx');
      cy.contains('dt', 'Namespace').next('dd').should('contain.text', 'spinnaker-dev');
    });
  });

  it('displays step-section-details tab content for Bake (Manifest)', () => {
    cy.visit('#/applications/kubernetesapp/executions');
    cy.get('.execution-details-button').contains('Execution Details').click();

    cy.get('span:contains("Bake (Manifest)")').click();

    cy.get('ul.nav.nav-pills li').contains('Baked Manifest').click();

    cy.get('.step-section-details a.clickable').click();

    cy.get('.modal-dialog')
      .should('exist')
      .within(() => {
        cy.contains('Baked Manifest').should('exist');
        cy.get('textarea').contains('# Source: nginx/templates/configmap.yaml').should('exist');
        cy.get('button.btn').contains('Close').click();
      });
  });

  it('displays step-section-details tab content for Deploy (Manifest)', () => {
    cy.visit('#/applications/kubernetesapp/executions');
    cy.get('.execution-details-button').contains('Execution Details').click();

    cy.get('span:contains("Deploy (Manifest)")').click();

    cy.get('ul.nav.nav-pills li').contains('Deploy Status').click();

    const resources = ['Service', 'ConfigMap', 'Deployment'];
    resources.forEach((resource) => {
      cy.get('.step-section-details').within(() => {
        cy.contains('dt', resource).should('exist');
      });
    });

    cy.get('.manifest-status')
      .eq(1)
      .within(() => {
        cy.contains('dt', 'ConfigMap').should('exist');
        cy.contains('textarea', 'dev-p01-nginx-v000').should('exist');
      });

    cy.get('.manifest-support-links')
      .eq(1)
      .within(() => {
        cy.contains('YAML').click();
      });

    cy.get('.modal-dialog')
      .should('exist')
      .within(() => {
        cy.contains('dev-p01-nginx-v000').should('exist');
        cy.get('textarea').contains('kind: ConfigMap').should('exist');
        cy.get('button.btn').contains('Close').click();
      });
  });

  it('displays step-section-details tab content for Rollout Restart (Manifest)', () => {
    cy.visit('#/applications/kubernetesapp/executions');
    cy.get('.execution-details-button').contains('Execution Details').click();

    cy.get('span:contains("Rollout Restart (Manifest)")').click();

    cy.get('ul.nav.nav-pills li')
      .first()
      .contains('Rollout Restart Status')
      .should('contain.text', 'Rollout Restart Status');

    cy.get('.step-section-details').should('exist');
  });

  it('displays step-section-details tab content for Scale (Manifest)', () => {
    cy.visit('#/applications/kubernetesapp/executions');
    cy.get('.execution-details-button').contains('Execution Details').click();

    cy.get('span:contains("Scale (Manifest)")').click();

    cy.get('ul.nav.nav-pills li').first().contains('Scale Manifest');

    cy.get('.step-section-details dl').within(() => {
      cy.contains('dt', 'Account').next('dd').should('contain.text', 'k8s-local');
      cy.contains('dt', 'Manifest').next('dd').should('contain.text', 'deployment dev-p01-nginx');
      cy.contains('dt', 'Namespace').next('dd').should('contain.text', 'spinnaker-dev');
    });
  });
});
