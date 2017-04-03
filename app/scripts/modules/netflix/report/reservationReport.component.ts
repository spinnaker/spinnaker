import {IComponentController, IComponentOptions, ILogService, IScope, module} from 'angular';

import {
  IExtractedReservation, IReservationReport, RESERVATION_REPORT_READER,
  ReservationReportReader
} from './reservationReport.read.service';

import './reservationReport.component.less';

interface IViewState {
  error: boolean;
  loading: boolean;
}

class ReservationReportComponentController implements IComponentController {

  public account: string;
  public region: string;
  public instanceType: string;
  public zones: string[];
  public isVpc: boolean;

  public report: IReservationReport;
  public reportData: IExtractedReservation[];
  public viewState: IViewState = {
    loading: true,
    error: false,
  };

  static get $inject(): string[] {
    return ['$log', '$scope', 'reservationReportReader'];
  }

  constructor(private $log: ILogService,
              private $scope: IScope,
              private reservationReportReader: ReservationReportReader) {}

  private setVpc(): void {
    this.reportData.forEach((row: IExtractedReservation) => {
      row.display = {
        reserved: this.isVpc ? row.reservations.reservedVpc : row.reservations.reserved,
        used: this.isVpc ? row.reservations.usedVpc : row.reservations.used,
        surplus: this.isVpc ? row.reservations.surplusVpc : row.reservations.surplus,
      };
    });
  }

  private setReportData(): void {
    if (this.viewState.loading || !this.account || !this.region || !this.instanceType || !this.zones) {
      return;
    }
    const data: IExtractedReservation[] =
      this.reservationReportReader.extractReservations(this.report.reservations, this.account, this.region, this.instanceType);
    this.reportData = data.filter((row: IExtractedReservation) => this.zones.includes(row.availabilityZone));
    this.setVpc();
  }

  public $onInit(): void {
    this.reservationReportReader.getReservations().then((report: IReservationReport) => {
      this.report = report;
      this.viewState.loading = false;
      this.setReportData();
    })
      .catch((error: any) => {
        this.$log.error('unexpected error retrieving reservation reports', error);
        this.viewState.loading = false;
        this.viewState.error = true;
      });

    this.$scope.$watchCollection(() => [this.instanceType, this.account, this.region, this.isVpc, this.zones], () => this.setReportData());
  }
}

class ReservationReportComponent implements IComponentOptions {
  public bindings: any = {
    account: '<',
    region: '<',
    instanceType: '<',
    zones: '<',
    isVpc: '<',
  };
  public controller: any = ReservationReportComponentController;
  public templateUrl: string = require('./reservationReport.component.html');
}

export const RESERVATION_REPORT_COMPONENT = 'spinnaker.amazon.serverGroup.report.reservationReport.directive';
module(RESERVATION_REPORT_COMPONENT, [
    RESERVATION_REPORT_READER,
    require('core/account/accountTag.directive.js')
  ])
  .component('reservationReport', new ReservationReportComponent());
