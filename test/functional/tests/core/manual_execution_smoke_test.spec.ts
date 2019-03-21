import { HomePage } from './pages/HomePage';
import { ApplicationsListPage } from './pages/ApplicationsListPage';
import { InfrastructurePage } from './pages/InfrastructurePage';
import { PipelinesListPage } from './pages/PipelinesListPage';
import { ManualExecutionModalPage } from './pages/ManualExecutionModalPage';
import { HeaderNavLocators } from './locators/HeaderNavLocators';

describe('Pipeline List', () => {
  describe('Manual execution link smoke test', () => {
    it('executes a simple Wait Stage pipeline', () => {
      const home = new HomePage();
      const apps = new ApplicationsListPage();
      const infra = new InfrastructurePage();
      const pipelines = new PipelinesListPage();
      const manualExecutionModal = new ManualExecutionModalPage();
      home.open();
      home.click(HeaderNavLocators.applicationsButton);
      apps.clickOnApplicationLink('compute');
      infra.click(HeaderNavLocators.pipelinesButton);
      pipelines.click(PipelinesListPage.locators.manualExecutionLink);
      manualExecutionModal.runPipeline('Simple Wait Pipeline');
      pipelines.awaitLocator(PipelinesListPage.locators.factories.pipelineWithStatus('RUNNING'), 30000);
      pipelines.awaitLocator(PipelinesListPage.locators.factories.pipelineWithStatus('SUCCEEDED'), 30000);
    });
  });
});
