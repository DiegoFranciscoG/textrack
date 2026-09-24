import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AqlInspection,
  AqlPlan,
  Attendance,
  BottleneckReport,
  BundleTrace,
  CutDetail,
  DailyPayroll,
  DashboardSnapshot,
  DefectType,
  FabricInspectionResult,
  InspectionLevel,
  Machine,
  MachineStop,
  Operator,
  OperatorDayDetail,
  OrderDetail,
  OrderSummary,
  Roll,
  RollTrace,
  ScanResult,
  Severity,
  Style,
  WeeklyPay,
} from './models';
import { RUNTIME_CONFIG } from './runtime-config';

export interface CreateOrder {
  styleId: number;
  customer: string;
  dueDate: string;
  lines: { sizeCode: string; color: string; quantity: number }[];
}

export interface CreateCut {
  color: string;
  maxBundleSize: number;
  sizeRatios: { sizeCode: string; piecesPerPly: number }[];
  rolls: { rollId: number; plies: number; metersUsed: number }[];
}

export interface CreateRoll {
  code: string;
  supplier: string;
  dyeLot: string;
  color: string;
  lengthM: number;
  widthCm: number;
}

export interface FabricInspection {
  inspectedLengthM: number;
  widthCm?: number;
  defects: { positionM: number; lengthMm: number; hole: boolean; defectTypeCode?: string }[];
}

export interface CreateAqlInspection {
  productionOrderId: number;
  lineId?: number;
  level: InspectionLevel;
  lotSize: number;
  aqlMajor: number;
  aqlMinor: number;
  defectiveUnits: number;
  defects: { defectTypeCode: string; severity?: Severity; quantity: number; bundleCode?: string; operationCode?: string }[];
}

/** Cliente tipado de la API REST v1. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(RUNTIME_CONFIG).apiUrl}/api/v1`;

  dashboard(date?: string): Observable<DashboardSnapshot> {
    return this.http.get<DashboardSnapshot>(`${this.base}/dashboard`, { params: dateParam(date) });
  }

  // ---------------------------------------------------------------- producción
  orders(): Observable<OrderSummary[]> {
    return this.http.get<OrderSummary[]>(`${this.base}/production-orders`);
  }

  order(id: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.base}/production-orders/${id}`);
  }

  createOrder(body: CreateOrder): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/production-orders`, body);
  }

  createCut(orderId: number, body: CreateCut): Observable<CutDetail> {
    return this.http.post<CutDetail>(`${this.base}/production-orders/${orderId}/cuts`, body);
  }

  cut(id: number): Observable<CutDetail> {
    return this.http.get<CutDetail>(`${this.base}/cuts/${id}`);
  }

  ticketsPdf(cutId: number): Observable<Blob> {
    return this.http.get(`${this.base}/cuts/${cutId}/tickets.pdf`, { responseType: 'blob' });
  }

  bottlenecks(orderId: number): Observable<BottleneckReport> {
    return this.http.get<BottleneckReport>(`${this.base}/reports/bottlenecks/${orderId}`);
  }

  sizes(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/catalogs/sizes`);
  }

  rolls(): Observable<Roll[]> {
    return this.http.get<Roll[]>(`${this.base}/fabric-rolls`);
  }

  receiveRoll(body: CreateRoll): Observable<Roll> {
    return this.http.post<Roll>(`${this.base}/fabric-rolls`, body);
  }

  inspectRoll(rollId: number, body: FabricInspection): Observable<FabricInspectionResult> {
    return this.http.post<FabricInspectionResult>(`${this.base}/fabric-rolls/${rollId}/inspection`, body);
  }

  // ---------------------------------------------------------------- ingeniería y planta
  styles(): Observable<Style[]> {
    return this.http.get<Style[]>(`${this.base}/styles`);
  }

  operators(): Observable<Operator[]> {
    return this.http.get<Operator[]>(`${this.base}/operators`);
  }

  machines(): Observable<Machine[]> {
    return this.http.get<Machine[]>(`${this.base}/machines`);
  }

  lines(): Observable<{ id: number; code: string; name: string }[]> {
    return this.http.get<{ id: number; code: string; name: string }[]>(`${this.base}/lines`);
  }

  attendances(date?: string): Observable<Attendance[]> {
    return this.http.get<Attendance[]>(`${this.base}/attendances`, { params: dateParam(date) });
  }

  checkIn(operatorId: number): Observable<Attendance> {
    return this.http.post<Attendance>(`${this.base}/attendances`, { operatorId });
  }

  checkOut(attendanceId: number): Observable<Attendance> {
    return this.http.put<Attendance>(`${this.base}/attendances/${attendanceId}/check-out`, {});
  }

  stops(date?: string): Observable<MachineStop[]> {
    return this.http.get<MachineStop[]>(`${this.base}/machine-stops`, { params: dateParam(date) });
  }

  reportStop(body: { machineId: number; reason: string; planned: boolean; notes?: string }): Observable<MachineStop> {
    return this.http.post<MachineStop>(`${this.base}/machine-stops`, body);
  }

  closeStop(id: number): Observable<MachineStop> {
    return this.http.put<MachineStop>(`${this.base}/machine-stops/${id}/close`, {});
  }

  // ---------------------------------------------------------------- destajo
  payroll(date?: string): Observable<DailyPayroll> {
    return this.http.get<DailyPayroll>(`${this.base}/payroll/daily`, { params: dateParam(date) });
  }

  payrollDetail(operatorId: number, date?: string): Observable<OperatorDayDetail> {
    return this.http.get<OperatorDayDetail>(`${this.base}/payroll/daily/${operatorId}`, { params: dateParam(date) });
  }

  weeklyPay(operatorId: number, weekStart: string): Observable<WeeklyPay> {
    return this.http.get<WeeklyPay>(`${this.base}/payroll/weekly/${operatorId}`, {
      params: new HttpParams().set('weekStart', weekStart),
    });
  }

  // ---------------------------------------------------------------- calidad y trazabilidad
  aqlPlan(lotSize: number, level: InspectionLevel, aql: number): Observable<AqlPlan> {
    const params = new HttpParams().set('lotSize', lotSize).set('level', level).set('aql', aql);
    return this.http.get<AqlPlan>(`${this.base}/quality/aql-plan`, { params });
  }

  defectTypes(): Observable<DefectType[]> {
    return this.http.get<DefectType[]>(`${this.base}/quality/defect-types`);
  }

  aqlInspections(): Observable<AqlInspection[]> {
    return this.http.get<AqlInspection[]>(`${this.base}/quality/aql-inspections`);
  }

  createAqlInspection(body: CreateAqlInspection): Observable<AqlInspection> {
    return this.http.post<AqlInspection>(`${this.base}/quality/aql-inspections`, body);
  }

  traceGarment(serial: string): Observable<BundleTrace> {
    return this.http.get<BundleTrace>(`${this.base}/traceability/garments/${encodeURIComponent(serial)}`);
  }

  traceBundle(code: string): Observable<BundleTrace> {
    return this.http.get<BundleTrace>(`${this.base}/traceability/bundles/${encodeURIComponent(code)}`);
  }

  traceRoll(code: string): Observable<RollTrace> {
    return this.http.get<RollTrace>(`${this.base}/traceability/rolls/${encodeURIComponent(code)}`);
  }

  scan(body: { clientReadingId: string; payload: string; operatorCode: string; scannedAt: string; deviceId: string }):
    Observable<ScanResult> {
    return this.http.post<ScanResult>(`${this.base}/readings`, body, {
      headers: { 'Idempotency-Key': body.clientReadingId },
    });
  }
}

function dateParam(date?: string): HttpParams {
  return date ? new HttpParams().set('date', date) : new HttpParams();
}
