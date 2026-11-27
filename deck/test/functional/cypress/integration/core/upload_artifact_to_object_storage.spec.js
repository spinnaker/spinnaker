import { registerDefaultFixtures } from '../../support';

describe('core: Upload Artifact to Object Storage Stage', () => {
  beforeEach(() => {
    registerDefaultFixtures();

    // Mock pipeline configs with pre-configured stage
    cy.intercept('/applications/compute/pipelineConfigs', {
      fixture: 'core/upload_artifact_to_object_storage/pipelineConfigs.json',
    });

    cy.intercept('/applications/compute/pipelineConfigs/Upload%20Artifact%20Pipeline', {
      fixture: 'core/upload_artifact_to_object_storage/pipelineConfigs.json',
    });

    // Mock pipeline executions
    cy.intercept('/applications/compute/pipelines?expand=false&limit=*', {
      fixture: 'core/upload_artifact_to_object_storage/pipelines.json',
    });

    cy.intercept('/applications/compute/pipelines*', {
      fixture: 'core/upload_artifact_to_object_storage/pipelines.json',
    });

    cy.intercept('/application/compute/pipelineLock', {
      fixture: 'default/pipelineLock.json',
    });

    cy.intercept('/executions?limit=*', {
      fixture: 'core/upload_artifact_to_object_storage/pipelines.json',
    });
  });

  it('displays the Upload Artifact to Object Storage stage in pipeline config', () => {
    cy.visit('#/applications/compute/executions');

    // Clear time boundary filter and select Pipeline view
    cy.get('.glyphicon-remove-sign').click();
    cy.get('.input-sm:contains("Pipeline")').select('Pipeline');

    // Click Configure on the pipeline
    cy.get('a:contains("Configure")').first().click({ force: true });

    // Click on the stage to select it
    cy.get('a:contains("Upload to S3")').click({ force: true });

    // Verify the stage form is displayed with the correct fields
    cy.get('.form-horizontal').should('exist');
    cy.get('h4:contains("Destination Configuration")').should('exist');
    cy.get('h4:contains("Source Artifact")').should('exist');
  });

  it('shows stage configuration fields', () => {
    cy.visit('#/applications/compute/executions');

    // Clear time boundary filter and select Pipeline view
    cy.get('.glyphicon-remove-sign').click();
    cy.get('.input-sm:contains("Pipeline")').select('Pipeline');

    cy.get('a:contains("Configure")').first().click({ force: true });
    cy.get('a:contains("Upload to S3")').click({ force: true });

    // Verify form fields exist
    cy.get('input[name="bucket"]').should('exist');
    cy.get('input[name="objectKeyPrefix"]').should('exist');

    // Verify pre-populated values from fixture
    cy.get('input[name="bucket"]').should('have.value', 'my-bucket');
    cy.get('input[name="objectKeyPrefix"]').should('have.value', 'releases/latest');
  });
});
