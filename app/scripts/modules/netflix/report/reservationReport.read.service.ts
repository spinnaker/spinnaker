import {IHttpPromiseCallbackArg, IHttpService, IPromise, module} from 'angular';

import {SETTINGS} from 'core/config/settings';

interface IReservationReportAccount {
  accountId: string;
  name: string;
  regions: string[];
}

interface IReservationAccountDetails {
  reserved: number;
  surplus: number;
  used: number;
}

interface IReservationVpcAccountDetails {
  reservedVpc?: number;
  surplusVpc?: number;
  usedVpc?: number;
}

interface IReservationAccount {
  [key: string]: IReservationAccountDetails;
}

export interface IReservation {
  accounts: IReservationAccount;
  availabilityZone?: string;
  availabilityZoneId: string;
  instanceType: string;
  os: string;
  region?: string;
  totalReserved: number;
  totalSurplus: number;
  totalUsed: number;
}

export interface IReservationReport {
  accounts: IReservationReportAccount[];
  end: number;
  errorsByRegion: any[];
  reservations: IReservation[];
  start: number;
  type: string;
}

export interface IExtractedReservation {
  availabilityZone: string;
  display?: IReservationAccountDetails;
  os: string;
  reservations: IReservationAccountDetails & IReservationVpcAccountDetails;
}

export class ReservationReportReader {
  constructor(private $http: IHttpService) { 'ngInject'; }

  public getReservations(): IPromise<IReservationReport> {
    return this.$http.get([SETTINGS.gateUrl, 'reports', 'reservation', 'v2'].join('/'))
      .then((response: IHttpPromiseCallbackArg<IReservationReport>) => response.data);
  }

  public extractReservations(reservations: IReservation[],
                             account: string,
                             region: string,
                             instanceType: string): IExtractedReservation[] {
    return reservations.filter((reservation: IReservation) =>
      reservation.region === region && reservation.instanceType === instanceType && reservation.accounts[account])
      .map((reservation: IReservation) => {
        return {
          availabilityZone: reservation.availabilityZone,
          os: reservation.os,
          reservations: reservation.accounts[account],
        };
      });
  }
}

export const RESERVATION_REPORT_READER = 'spinnaker.amazon.instance.report.reservation.read.service';
module(RESERVATION_REPORT_READER, [])
  .service('reservationReportReader', ReservationReportReader);
