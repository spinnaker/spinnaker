import {module} from 'angular';
import {SCROLL_TO_SERVICE, ScrollToService} from 'core/utils/scrollTo/scrollTo.service';

export interface WizardPageState {
  done: boolean;
  blocked: boolean;
  rendered: boolean;
  current: boolean;
  dirty: boolean;
  markCompleteOnView: boolean;
  required: boolean;
}

export interface WizardPage {
  state: WizardPageState;
  key: string;
  label: string;
}

export class V2ModalWizardService {

  public renderedPages: WizardPage[] = [];
  public pageRegistry: WizardPage[] = [];
  public currentPage: WizardPage;
  public heading: string;
  public pageOffset: number = -30;

  public constructor(private scrollToService: ScrollToService) {}

  public setHeading(heading: string): void {
    this.heading = heading;
  }

  public setPageOffset(offset: number): void {
    this.pageOffset = offset;
  }

  public getPage(key: string): WizardPage {
    return this.pageRegistry.find(p => p.key === key);
  }

  public markDirty(key: string): void {
    this.getPage(key).state.dirty = true;
  }

  public markClean(key: string): void {
    this.getPage(key).state.dirty = false;
  }

  public markComplete(key: string): void {
    this.getPage(key).state.done = true;
  }

  public markIncomplete(key: string): void {
    this.getPage(key).state.done = false;
  }

  public setCurrentPage(page: WizardPage, skipScroll?: boolean): void {
    this.pageRegistry.forEach(test => test.state.current = (test === page));
    this.currentPage = page;
    this.markClean(page.key);

    if (page.state.markCompleteOnView) {
      this.markComplete(page.key);
    }

    if (!skipScroll) {
      this.scrollToService.scrollTo(`[waypoint="${page.key}"]`, '[waypoint-container]', 143 + this.pageOffset);
    }
  }

  public registerPage(key: string, label: string, state?: WizardPageState): void {
    state = state ||
      {
        done: false,
        blocked: true,
        rendered: true,
        current: false,
        dirty: false,
        markCompleteOnView: false,
        required: false,
      };
    this.pageRegistry.push({key: key, label: label, state: state});
    this.renderPages();
  }

  public renderPages(): void {
    let renderedPages: WizardPage[] = this.pageRegistry.filter((page) => page.state.rendered);
    this.renderedPages = renderedPages;
    if (renderedPages.length === 1) {
      this.setCurrentPage(renderedPages[0]);
    }
  }

  public isComplete(): boolean {
    return this.renderedPages.map(p => p.state)
      .filter(s => s.rendered && s.required)
      .every(s => s.done && !s.dirty);
  }

  public allPagesVisited(): boolean {
    return this.renderedPages.map(p => p.state)
      .filter(s => s.rendered && s.required)
      .every(s => s.done);
  }

  public setRendered(key: string, rendered: boolean): void {
    this.pageRegistry.filter((page) => page.key === key)
      .forEach((page) => page.state.rendered = rendered);
    this.renderPages();
  }

  public includePage(key: string): void {
    this.setRendered(key, true);
  }

  public excludePage(key: string): void {
    this.setRendered(key, false);
  }

  public resetWizard(): void {
    this.renderedPages.length = 0;
    this.pageRegistry.length = 0;
    this.currentPage = null;
    this.heading = null;
    this.pageOffset = -30;
  }

}

const moduleName = 'spinnaker.core.modalWizard.service.v2';

module(moduleName, [SCROLL_TO_SERVICE])
  .service('v2modalWizardService', V2ModalWizardService);

export default moduleName;
