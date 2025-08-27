import { registerDefaultFixtures } from '../../../support';

describe('core: Project', () => {
  before(() => {
    require('events').EventEmitter.defaultMaxListeners = 15;
  });

  beforeEach(() => {
    registerDefaultFixtures();

    cy.intercept('/projects/kubernetesproject', { fixture: 'core/projects/kubernetesproject/index.json' });
    cy.intercept('/projects/kubernetesproject/clusters', {
      fixture: 'core/projects/kubernetesproject/clusters.json',
    });
    cy.intercept('/projects/kubernetesproject/pipelines*', {
      fixture: 'core/projects/kubernetesproject/pipelines.json',
    });
    cy.intercept('/applications/kubernetesapp/pipelineConfigs', {
      fixture: 'kubernetes/pipelines/pipelineConfigs.json',
    });
    cy.intercept('/applications/kubernetesapp/serverGroups', {
      fixture: 'kubernetes/clusters/serverGroups.json',
    });
  });

  it('should show project dashboard', () => {
    cy.visit('#/projects/kubernetesproject/dashboard');

    cy.get('.project-header .container h2').within(() => {
      cy.get('.project-name').should('contain.text', 'kubernetesproject /');

      cy.get('.project-view .dropdown .clickable').click();
      cy.get('.project-view .dropdown-menu').within(() => {
        cy.contains('a', 'kubernetesapp')
          .should('have.attr', 'href', '#/projects/kubernetesproject/applications/kubernetesapp')
          .click();
      });
      cy.url().should('match', /#\/projects\/kubernetesproject\/applications\/kubernetesapp\/clusters$/);
      cy.get('.project-view .dropdown .clickable').click();
      cy.get('.project-view .dropdown-menu').within(() => {
        cy.contains('a', 'Project Dashboard')
          .should('have.attr', 'href', '#/projects/kubernetesproject/dashboard')
          .click();
      });
    });

    cy.get('.pull-right .configure-project-link')
      .should('contain.text', 'Project Configuration')
      .and('have.class', 'btn-configure');
  });

  it('should opens configure project and validates content', async () => {
    cy.visit('#/projects/kubernetesproject/dashboard');
    cy.window().its('angular').should('exist');

    // Open modal
    cy.get('.pull-right .configure-project-link').click();
    cy.get('.modal-content').should('be.visible');

    // Header/title
    cy.get('.modal-content .modal-header .modal-title').should('have.text', 'Configure Project');

    // Wizard nav has 4 steps
    cy.get('.modal-content .wizard-navigation li').should('have.length', 4);
    cy.get('.modal-content .wizard-navigation').within(() => {
      cy.contains('a.clickable', 'Project Attributes').should('exist');
      cy.contains('a.clickable', 'Applications').should('exist');
      cy.contains('a.clickable', 'Clusters').should('exist');
      cy.contains('a.clickable', 'Pipelines').should('exist');
    });

    // Project Attributes page fields & values
    cy.get('.modal-content input[name="name"]').should('have.value', 'kubernetesproject');
    cy.get('.modal-content input[name="email"]').should('have.value', 'sbws@google.com');

    // Go to Applications
    cy.get('.modal-content .wizard-navigation').contains('Applications').click();
    cy.get('.modal-content .modal-page:contains("Applications")').should('be.visible');
    cy.get('.modal-content input[name="config.applications"]').should('have.value', 'kubernetesapp');
    cy.get('.modal-content .Select-value-label').contains('kubernetesapp').should('exist');

    // Go to Clusters
    cy.get('.modal-content .wizard-navigation').contains('Clusters').click();
    cy.get('.modal-content .modal-page:contains("Clusters")').should('be.visible');
    cy.get('.modal-content .ConfigureProject-Clusters table thead').within(() => {
      cy.contains('td', 'Application');
      cy.contains('td', 'Account');
      cy.contains('td', 'Stack');
      cy.contains('td', 'Detail');
    });
    cy.get('.modal-content .ConfigureProject-Clusters tbody tr')
      .first()
      .within(() => {
        cy.get('label input[type="checkbox"]').should('be.checked'); // "All"
        cy.get('input[name="config.clusters[0].account"]').should('have.value', 'k8s-local');
        cy.get('input[name="config.clusters[0].stack"]').should('have.value', '*');
        cy.get('input[name="config.clusters[0].detail"]').should('have.value', '*');
      });
    cy.get('.modal-content .ConfigureProject-Clusters').contains('a.button', 'Add Cluster').should('exist');

    // Go to Pipelines
    cy.get('.modal-content .wizard-navigation').contains('Pipelines').click();
    cy.get('.modal-content .modal-page:contains("Pipelines")').should('be.visible');
    cy.get('.modal-content .ConfigureProject-Pipelines tbody tr').should('have.length', 2);
    cy.get('.modal-content .ConfigureProject-Pipelines tbody tr')
      .eq(0)
      .within(() => {
        cy.get('input[name="config.pipelineConfigs[0].application"]').should('have.value', 'kubernetesapp');
        cy.get('.Select-value-label').contains('kubernetesapp');
        cy.get('.Select-value-label').contains('bake_and_deploy_manifest');
      });
    cy.get('.modal-content .ConfigureProject-Pipelines tbody tr')
      .eq(1)
      .within(() => {
        cy.get('.Select-value-label').contains('kubernetesapp');
        cy.get('.Select-value-label').contains('deployment');
      });
    cy.get('.modal-content .ConfigureProject-Pipelines').contains('a.button', 'Add Pipeline').should('exist');

    // Close via Cancel
    cy.get('.modal-footer button.btn.btn-default').contains('Cancel').click();
  });

  it('shows application status and interacts with region filters', () => {
    cy.visit('#/projects/kubernetesproject/dashboard');
    cy.window().its('angular').should('exist');

    // Header + filter dropdown
    cy.contains('h3', 'Application Status').within(() => {
      cy.get('.region-filter-button').should('contain.text', 'Filter by region / namespace');
      cy.get('h6.dropdown-toggle').click();
    });

    // Ensure all 3 regions are present
    cy.get('.project-column .dropdown-menu').within(() => {
      cy.contains('label', 'dev').should('exist');
      cy.contains('label', 'prod').should('exist');
      cy.contains('label', 'test').should('exist');
      cy.contains('a', 'Clear all').should('exist');
    });

    // Rollup summary (24 instances now)
    cy.get('project-cluster .rollup-entry .rollup-summary').within(() => {
      cy.get('.account-tag').should('contain.text', 'k8s-local');
      cy.get('.cluster-name').should('contain.text', '*-*');
      cy.get('.cluster-health').first().should('contain.text', '1 Application');
      cy.contains('.health-counts', '24').should('contain.text', '100%');
    });

    // Expand details
    cy.get('project-cluster .rollup-entry .row.clickable').click();
    cy.get('project-cluster .rollup-details').should('not.exist');
    cy.get('project-cluster .rollup-entry .row.clickable').click();

    // Table shows 3 region columns initially (dev/prod/test)
    cy.get('project-cluster .rollup-details thead').within(() => {
      cy.get('th').contains('Last Push').should('exist');
      cy.get('th').filter((_, el) => el.innerText.trim() === 'dev').should('have.length', 1);
      cy.get('th').filter((_, el) => el.innerText.trim() === 'prod').should('have.length', 1);
      cy.get('th').filter((_, el) => el.innerText.trim() === 'test').should('have.length', 1);
    });

    // App row basics
    cy.get('project-cluster .rollup-details tbody tr').first().within(() => {
      cy.get('td a.heavy')
        .should('contain.text', 'KUBERNETESAPP')
        .and('have.attr', 'href')
        .and('include', '#/projects/kubernetesproject/applications/kubernetesapp/clusters?acct=k8s-local');

      cy.get('ul.list-unstyled li').should('have.length.at.least', 1);
      cy.get('ul.list-unstyled').should('contain.text', 'nginx');
      cy.get('td .small').should('contain.text', 'ago'); // relative time
    });

    // --- Interact with filters ---

    // Select dev + prod, leave test off
    cy.contains('h3', 'Application Status').find('h6.dropdown-toggle').click();
    cy.get('.project-column .dropdown-menu').within(() => {
      cy.contains('li', 'dev').click().find('input[type="checkbox"]').should('be.checked');
      cy.contains('li', 'prod').click().find('input[type="checkbox"]').should('be.checked');
      cy.contains('li', 'test').find('input[type="checkbox"]').should('not.be.checked');
    });
    cy.contains('h3', 'Application Status').find('h6.dropdown-toggle').click();

    // Now table should only show dev + prod region columns
    cy.get('project-cluster .rollup-details thead').within(() => {
      cy.get('th').filter((_, el) => el.innerText.trim() === 'dev').should('have.length', 1);
      cy.get('th').filter((_, el) => el.innerText.trim() === 'prod').should('have.length', 1);
      cy.get('th').filter((_, el) => el.innerText.trim() === 'test').should('have.length', 0);
    });

    // And row should only have 2 region links (dev + prod)
    cy.get('project-cluster .rollup-details tbody tr').first().within(() => {
      cy.get('td a[href*="reg="]').should('have.length', 2);
      cy.get('td a[href*="reg=dev"]').should('have.length', 1);
      cy.get('td a[href*="reg=prod"]').should('have.length', 1);
    });

    // Now, enable test as well and ensure all 3 links are back
    cy.contains('h3', 'Application Status').find('h6.dropdown-toggle').click();
    cy.get('.project-column .dropdown-menu').within(() => {
      cy.contains('li', 'test').click().find('input[type="checkbox"]').should('be.checked');
    });
    cy.contains('h3', 'Application Status').find('h6.dropdown-toggle').click();

    cy.get('project-cluster .rollup-details thead').within(() => {
      ['dev', 'prod', 'test'].forEach((r) =>
        cy.get('th').filter((_, el) => el.innerText.trim() === r).should('have.length', 1),
      );
    });

    // Finally, Open dropdown and Clear all
    cy.contains('h3', 'Application Status').find('h6.dropdown-toggle').click();
    cy.get('.project-column .dropdown-menu').within(() => cy.contains('a', 'Clear all').click());
    // close dropdown to trigger any re-render
    cy.contains('h3', 'Application Status').find('h6.dropdown-toggle').click();

    cy.get('project-cluster .rollup-details thead').within(() => {
      const regionHeaders = ['dev', 'prod', 'test'];
      cy.get('th').then(($ths) => {
        const texts = [...$ths].map((el) => el.innerText.trim());
        regionHeaders.forEach((r) => expect(texts.includes(r)).to.eq(true));
        expect(texts.includes('Last Push')).to.eq(true);
      });
    });
    //
    cy.get('project-cluster .rollup-details tbody tr').first().find('td a[href*="reg="]').should('have.length', 3);
  });

  it('should show pipeline status', () => {
    cy.visit('#/projects/kubernetesproject/dashboard');
    cy.window().its('angular').should('exist');

    cy.get('.project-column').eq(1).within(() => {
      cy.contains('h3', 'Pipeline Status').should('exist');

      // First pipeline: deployment (3 stages)
      cy.get('project-pipeline').eq(0).within(() => {
        cy.get('.execution-title a').should('contain.text', 'KUBERNETESAPP: deployment');
        cy.get('.execution-bar .execution-marker')
          .should('have.length', 3)
          .each(($m) => cy.wrap($m).should('have.class', 'execution-marker-succeeded'));
        cy.get('.execution-bar .execution-marker').each(($m) =>
          cy.wrap($m).should('have.class', 'stage-type-deploymanifest'),
        );
        cy.get('.duration').each(($d) => cy.wrap($d).invoke('text').should('match', /\d{2}:\d{2}/));
      });

      // Second pipeline: bake_and_deploy_manifest (5 stages)
      cy.get('project-pipeline').eq(1).within(() => {
        cy.get('.execution-title a').should('contain.text', 'KUBERNETESAPP: bake_and_deploy_manifest');
        const classes = [
          'stage-type-deletemanifest',
          'stage-type-bakemanifest',
          'stage-type-deploymanifest',
          'stage-type-rollingrestartmanifest',
          'stage-type-scalemanifest',
        ];
        cy.get('.execution-bar .execution-marker').should('have.length', 5);
        classes.forEach((cls) => {
          cy.get(`.execution-bar .execution-marker.${cls}`).should('have.length.at.least', 1);
        });
        cy.get('.execution-bar .execution-marker').each(($m) =>
          cy.wrap($m).should('have.class', 'execution-marker-succeeded'),
        );
        cy.get('.duration').each(($d) => cy.wrap($d).invoke('text').should('match', /\d{2}:\d{2}/));
      });
    });
  });

  afterEach(() => {
    cy.window().then((win) => {
      win.removeAllListeners && win.removeAllListeners();
    });
  });
});
