import { registerDefaultFixtures, ReactSelect } from '../../support';
describe('google: Clone Modal Build custom instance type', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.route('/applications/compute/serverGroups', 'fixture:google/clone/serverGroups.json');
    cy.route('/images/find?*', 'fixture:google/shared/images.json');
    cy.route(
      '/applications/compute/serverGroups/**/compute-v000?includeDetails=false',
      'fixture:google/clone/serverGroup.compute-v000.json',
    );
  });

  it('provides options for cores and memory', () => {
    cy.visit('#/applications/compute/clusters');

    // click v000 cluster
    cy.get('.sub-group:contains("compute-engine")')
      .find('.server-group:contains("v000")')
      .click({ force: true });

    // clone dialog
    cy.get('button:contains("Server Group Actions")').click();
    cy.get('a:contains("Clone")').click();

    // choose the first ubuntu-1404-trusty-v20181002
    const imageDropdown = ReactSelect('v2-wizard-page[key=location] div.form-group:contains("Image")');
    imageDropdown.get();
    imageDropdown.toggleDropdown();
    imageDropdown.type('ubuntu-1404-trusty-v20181002');
    imageDropdown.select(0);

    // choose "Build Custom" instance type
    cy.get('v2-wizard-page[key="instance-type"] button:contains("Build Custom")').click();

    // open 'cores' dropdown and assert some values
    const coresDropdown = ReactSelect('v2-wizard-page[key="instance-type"] div.row:contains("Cores")');
    coresDropdown.toggleDropdown();
    coresDropdown.getOptions().then(options => {
      const cores = Array.from(options).map(o => o.innerHTML);
      expect(cores).to.include('1');
      expect(cores).to.include('4');
      expect(cores).to.include('16');
      expect(cores).to.include('32');
      expect(cores).to.include('64');
      expect(cores).to.include('96');
    });
    coresDropdown.toggleDropdown();

    // open 'memory' dropdown and assert some values
    const memoryDropdown = ReactSelect('v2-wizard-page[key="instance-type"] div.row:contains("Memory")');
    memoryDropdown.toggleDropdown();
    memoryDropdown.getOptions().then(options => {
      const memory = Array.from(options).map(o => o.innerHTML);
      expect(memory).to.include('1');
      expect(memory).to.include('1.25');
      expect(memory).to.include('4.5');
      expect(memory).to.include('5.75');
      expect(memory).to.include('6.5');
    });
    memoryDropdown.toggleDropdown();
  });
});
