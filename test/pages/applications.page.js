module.exports = {
  url: '#/applications',
  header: element(by.css('[data-purpose="view-header"]')),
  menu: element(by.buttonText('Actions')),
  createApplicationMenuItem: element(by.linkText('Create Application')),
};
