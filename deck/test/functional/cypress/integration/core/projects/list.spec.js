import { registerDefaultFixtures } from '../../../support';

describe('core: Projects List', () => {
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
    cy.intercept('POST', '/tasks', {ref: '/tasks/01K2EKHP4S2W1V9J61W2QR8PQ3'});
    cy.intercept('/tasks/01K2EKHP4S2W1V9J61W2QR8PQ3', {
      fixture: 'core/projects/task/task.success.json',
    });
  });

  it('should show a list of projects', () => {
    cy.visit('#/projects');

    cy.get('.infrastructure-section.search-header .container').within(() => {
      cy.get('.header-section .search-label').should('have.text', 'Projects');
      cy.get('input[placeholder="Search projects"]')
        .should('have.attr', 'type', 'search')
        .and('have.class', 'form-control');
      cy.get('#insight-menu a.btn.btn-primary').should('contain.text', 'Create Project');
    });

    cy.get('table.table-hover thead tr').within(() => {
      cy.contains('th', 'Name');
      cy.contains('th', 'Created');
      cy.contains('th', 'Updated');
      cy.contains('th', 'Owner');
    });

    cy.get('table.table-hover').should('exist');

    cy.get('tbody tr').should('have.length.at.least', 2);

    cy.get('tbody tr')
      .eq(0)
      .within(() => {
        cy.get('td a').should('have.attr', 'href', '#/projects/default/dashboard').and('contain.text', 'default');
        cy.get('td').eq(1).should('contain.text', '2025-01-15 15:21:11');
        cy.get('td').eq(2).should('contain.text', '2025-08-11 17:23:41');
        cy.get('td').eq(3).should('contain.text', 'sbws@google.com');
      });

    cy.get('tbody tr')
      .eq(1)
      .within(() => {
        cy.get('td a')
          .should('have.attr', 'href', '#/projects/kubernetesproject/dashboard')
          .and('contain.text', 'kubernetes');
        cy.get('td').eq(1).should('contain.text', '2025-08-11 15:33:38');
        cy.get('td').eq(2).should('contain.text', '2025-08-11 21:52:47');
        cy.get('td').eq(3).should('contain.text', 'sbws@google.com');
      });

    cy.get('ul.pagination').within(() => {
      cy.get('li').eq(0).should('have.class', 'disabled').find('a').should('have.text', '«');
      cy.get('li').eq(1).should('have.class', 'disabled').find('a').should('have.text', '‹');
      cy.get('li').eq(2).should('have.class', 'active').find('a').should('contain.text', '1');
      cy.get('li').eq(3).should('have.class', 'disabled').find('a').should('have.text', '›');
      cy.get('li').eq(4).should('have.class', 'disabled').find('a').should('have.text', '»');
    });
  });

  it('navigates to the project dashboard when clicking a project name', () => {
    cy.visit('#/projects');

    cy.get('tbody tr')
      .eq(1)
      .within(() => {
        cy.get('td a').contains('kubernetesproject').click();
      });

    cy.url().should('match', /#\/projects\/kubernetesproject\/dashboard$/);
  });

  it('should creates a new project via project configuration modal', () => {

    cy.intercept('/projects/newproject', { fixture: 'core/projects/newproject/index.json' });
    cy.intercept('/projects/newproject/pipelines*', []);

    const newName = 'newproject';
    const newEmail = 'team@example.com';

    cy.visit('#/projects');

    // Open Configure Project
    cy.get('#insight-menu a.btn.btn-primary').should('contain.text', 'Create Project').click();

    // Modal shows up
    cy.get('.modal-content').should('be.visible');
    cy.get('.modal-title').should('contain.text', 'Configure Project');

    // Fill form for new project
    cy.get('.modal-content input[name="name"]').clear().type(newName);
    cy.get('.modal-content input[name="email"]').clear().type(newEmail);

    // Verify steps are present
    cy.get('.wizard-navigation li').should('have.length', 4);

    // Save
    cy.get('.modal-footer .btn.btn-primary[type="submit"]').click();

    // Close
    cy.get('.modal-footer button.btn.btn-primary').contains('Close').click();

    cy.url().should('match', /#\/projects\/newproject\/dashboard$/);

  });

  it('should filters projects via the search input', () => {
    cy.visit('#/projects');

    // alias the search box
    cy.get('input[placeholder="Search projects"]').as('search');

    // baseline: we start with at least 2 rows
    cy.get('tbody tr').should('have.length.at.least', 2);

    // filter: partial match for kubernetesproject
    cy.get('@search').type('kube');
    cy.get('tbody tr').should('have.length', 1);
    cy.get('tbody tr td a[href="#/projects/kubernetesproject/dashboard"]').should('exist');

    // case-insensitive match for default
    cy.get('@search').clear().type('DEFAULT');
    cy.get('tbody tr').should('have.length', 1);
    cy.get('tbody tr td a[href="#/projects/default/dashboard"]').should('exist');

    // another partial for default
    cy.get('@search').clear().type('def');
    cy.get('tbody tr').should('have.length', 1);

    // no results state
    cy.get('@search').clear().type('zzzzzz');
    cy.get('tbody tr').should('have.length', 0);

    // reset to all results
    cy.get('@search').clear();
    cy.get('tbody tr').should('have.length.at.least', 2);

    // pressing Enter shouldn’t navigate away
    cy.get('@search').type('kube{enter}');
    cy.url().should('include', '#/projects');
  });

  it('should paginate the projects list', () => {
    // Stub the list to have 14 records
    cy.intercept('/projects', { fixture: 'core/projects/pagination/index.json' });

    cy.visit('#/projects');

    // Page 1: should show 12 rows
    cy.get('table.table-hover').should('exist');
    cy.get('tbody tr').should('have.length', 12);

    // Pagination state on page 1: «, ‹ disabled; 1 active; 2 exists; ›, » enabled
    cy.get('ul.pagination').within(() => {
      cy.get('li').eq(0).should('have.class', 'disabled').find('a').should('have.text', '«');
      cy.get('li').eq(1).should('have.class', 'disabled').find('a').should('have.text', '‹');
      cy.contains('li', '1').should('have.class', 'active');
      cy.contains('li', '2').should('not.have.class', 'active');
      cy.contains('li', '›').should('not.have.class', 'disabled');
      cy.contains('li', '»').should('not.have.class', 'disabled');
    });

    // Go to page 2
    cy.get('ul.pagination').contains('li a', '2').click();

    // Page 2: 2 rows
    cy.get('tbody tr').should('have.length', 2);

    // Pagination state on page 2: «, ‹ enabled; 2 active; ›, » disabled
    cy.get('ul.pagination').within(() => {
      cy.contains('li', '«').should('not.have.class', 'disabled');
      cy.contains('li', '‹').should('not.have.class', 'disabled');
      cy.contains('li', '2').should('have.class', 'active');
      cy.contains('li', '›').should('have.class', 'disabled');
      cy.contains('li', '»').should('have.class', 'disabled');
    });

    // Back to page 1 via ‹
    cy.get('ul.pagination').contains('li a', '‹').click();
    cy.get('tbody tr').should('have.length', 12);
    cy.get('ul.pagination').contains('li', '1').should('have.class', 'active');
  });

});
