module.exports = {
  url: '#/applications',
  header: element(by.css('[data-purpose="view-header"]')),
  menu: element(by.css('[data-purpose="applications-menu"] button')),
  createApplicationMenuItem: element(by.css('[data-purpose="applications-menu"] li a')),
};
