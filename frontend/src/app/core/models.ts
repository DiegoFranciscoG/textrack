// Tipos que reflejan los DTO de la API (backend/src/main/java/.../dto).

export type Role = 'ADMIN' | 'PLANNER' | 'SUPERVISOR' | 'QUALITY' | 'SCANNER' | 'VIEWER';

export interface UserSummary {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  user: UserSummary;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
}

// ---------------------------------------------------------------- tablero

export interface Kpi {
  attendedMinutes: number;
  plannedBusyMinutes: number;
  actualProductionMinutes: number;
  earnedMinutes: number;
  availability: number;
  performance: number;
  quality: number;
  oee: number;
  efficiency: number;
  qualityMeasured: boolean;
  pieces: number;
}

export interface LineKpi {
  lineId: number;
  lineCode: string;
  lineName: string;
  operatorsPresent: number;
  kpi: Kpi;
}

export interface OperatorKpi {
  operatorId: number;
  operatorCode: string;
  operatorName: string;
  lineCode?: string;
  pieces: number;
  earnedMinutes: number;
  attendedMinutes?: number;
  efficiency?: number;
  ordinaryPay: number;
  totalPay: number;
  floor: number;
  belowFloor: boolean;
}

export interface RecentReading {
  id: number;
  scannedAt: string;
  operatorCode: string;
  operatorName: string;
  lineCode?: string;
  bundleCode: string;
  operationCode: string;
  quantity: number;
  source: string;
}

export interface ActiveStop {
  id: number;
  machineCode: string;
  lineCode?: string;
  reason: string;
  planned: boolean;
  startedAt: string;
  minutes: number;
}

export interface DashboardSnapshot {
  workDate: string;
  generatedAt: string;
  plant: Kpi;
  lines: LineKpi[];
  operators: OperatorKpi[];
  recentReadings: RecentReading[];
  activeStops: ActiveStop[];
  forgedTicketsToday: number;
  operatorsBelowFloor: number;
}

// ---------------------------------------------------------------- producción

export type OrderStatus = 'PLANNED' | 'CUTTING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface OrderSummary {
  id: number;
  code: string;
  styleCode: string;
  styleName: string;
  customer: string;
  dueDate: string;
  status: OrderStatus;
  createdAt: string;
}

export interface OrderLine {
  sizeCode: string;
  color: string;
  quantity: number;
  cutQuantity: number;
}

export interface CutSummary {
  id: number;
  code: string;
  color: string;
  bundles: number;
  pieces: number;
  createdAt: string;
}

export interface OrderDetail extends OrderSummary {
  styleId: number;
  totalQuantity: number;
  cutQuantity: number;
  finishedQuantity: number;
  lines: OrderLine[];
  cuts: CutSummary[];
}

export interface Bundle {
  id: number;
  code: string;
  bundleNumber: number;
  sizeCode: string;
  color: string;
  quantity: number;
  rollCode: string;
  dyeLot: string;
}

export interface CutDetail {
  id: number;
  code: string;
  orderCode: string;
  color: string;
  maxBundleSize: number;
  createdAt: string;
  pieces: number;
  tickets: number;
  rolls: { rollCode: string; dyeLot: string; plies: number; metersUsed: number }[];
  bundles: Bundle[];
}

export type RollStatus = 'RECEIVED' | 'APPROVED' | 'REJECTED' | 'EXHAUSTED';

export interface Roll {
  id: number;
  code: string;
  supplier: string;
  dyeLot: string;
  color: string;
  lengthM: number;
  widthCm: number;
  remainingM: number;
  status: RollStatus;
  receivedAt: string;
  totalPoints?: number;
  pointsPer100SqYd?: number;
}

export interface FabricInspectionResult {
  rollId: number;
  rollCode: string;
  totalPoints: number;
  pointsPer100SqYd: number;
  maxPointsAllowed: number;
  accepted: boolean;
  rollStatus: RollStatus;
}

// ---------------------------------------------------------------- ingeniería y planta

export interface Operation {
  id: number;
  sequence: number;
  code: string;
  name: string;
  machineType: string;
  samMinutes: number;
  currentRateUsd?: number;
}

export interface Style {
  id: number;
  code: string;
  name: string;
  garmentType: string;
  active: boolean;
  totalSam: number;
  operations: Operation[];
}

export interface Operator {
  id: number;
  code: string;
  fullName: string;
  lineCode?: string;
  active: boolean;
}

export interface Machine {
  id: number;
  code: string;
  machineType: string;
  lineCode: string;
  active: boolean;
}

export interface Attendance {
  id: number;
  operatorId: number;
  operatorCode: string;
  workDate: string;
  checkIn: string;
  checkOut?: string;
  breakMinutes: number;
}

export interface MachineStop {
  id: number;
  machineId: number;
  machineCode: string;
  reason: string;
  planned: boolean;
  startedAt: string;
  endedAt?: string;
  notes?: string;
}

// ---------------------------------------------------------------- destajo

export interface OperatorPay {
  operatorId: number;
  operatorCode: string;
  operatorName: string;
  lineCode?: string;
  workDate: string;
  pieces: number;
  earnedMinutes: number;
  attendedMinutes?: number;
  efficiency?: number;
  ordinaryPay: number;
  extraPay: number;
  premiumPay: number;
  floor: number;
  topUp: number;
  totalPay: number;
  belowFloor: boolean;
  weekend: boolean;
  attendanceRecorded: boolean;
}

export interface DailyPayroll {
  workDate: string;
  sbu: number;
  hourlyFloor: number;
  legalReference: string;
  operatorsBelowFloor: number;
  totalPay: number;
  totalTopUp: number;
  operators: OperatorPay[];
}

export interface PayLine {
  scannedAt: string;
  operationCode: string;
  quantity: number;
  rateUsd: number;
  baseAmount: number;
  premium: string;
  premiumPercent: number;
  premiumAmount: number;
}

export interface OperatorDayDetail {
  summary: OperatorPay;
  lines: PayLine[];
}

export interface WeeklyPay {
  operatorId: number;
  operatorCode: string;
  operatorName: string;
  weekStart: string;
  days: OperatorPay[];
  weeklyRest: { weekdayAverage: number; minimumDaily: number; dailyRate: number; amount: number };
  weekTotal: number;
}

// ---------------------------------------------------------------- calidad y reportes

export type InspectionLevel = 'S1' | 'S2' | 'S3' | 'S4' | 'I' | 'II' | 'III';
export type Severity = 'CRITICAL' | 'MAJOR' | 'MINOR';

export interface AqlPlan {
  lotSize: number;
  level: InspectionLevel;
  aql: number;
  initialLetter: string;
  planLetter: string;
  sampleSize: number;
  acceptNumber: number;
  rejectNumber: number;
  fullInspection: boolean;
  standardEdition: string;
}

export interface DefectType {
  code: string;
  name: string;
  category: string;
  defaultSeverity: Severity;
}

export interface AqlInspection {
  id: number;
  orderCode: string;
  lineCode?: string;
  level: string;
  lotSize: number;
  aqlMajor: number;
  aqlMinor: number;
  codeLetter: string;
  sampleSize: number;
  majorAccept: number;
  majorReject: number;
  minorAccept: number;
  minorReject: number;
  criticalFound: number;
  majorFound: number;
  minorFound: number;
  defectiveUnits: number;
  result: 'ACCEPTED' | 'REJECTED';
  standardEdition: string;
  inspectedAt: string;
}

export interface OperationLoad {
  sequence: number;
  code: string;
  name: string;
  samMinutes: number;
  operators: number;
  completedPieces: number;
  pendingPieces: number;
  wipBefore: number;
  capacityPerHour: number;
  remainingMinutes?: number;
  bottleneck: boolean;
}

export interface BottleneckReport {
  orderId: number;
  orderCode: string;
  styleCode: string;
  totalPieces: number;
  bottleneckCode?: string;
  estimatedHoursToFinish?: number;
  balanceEfficiency?: number;
  operations: OperationLoad[];
}

export interface RollInfo {
  code: string;
  dyeLot: string;
  supplier: string;
  pointsPer100SqYd?: number;
  accepted?: boolean;
}

export interface BundleTrace {
  bundleCode: string;
  garmentNumber?: number;
  garmentSerial?: string;
  sizeCode: string;
  color: string;
  quantity: number;
  cutCode: string;
  cutAt: string;
  orderCode: string;
  customer: string;
  styleCode: string;
  styleName: string;
  roll: RollInfo;
  operations: {
    ticketId: string;
    sequence: number;
    operationCode: string;
    operationName: string;
    scannedAt?: string;
    operatorCode?: string;
    operatorName?: string;
  }[];
  defects: {
    defectTypeCode: string;
    defectName: string;
    severity: string;
    quantity: number;
    operationCode?: string;
    inspectedAt: string;
  }[];
}

export interface RollTrace {
  roll: RollInfo;
  color: string;
  lengthM: number;
  remainingM: number;
  status: string;
  bundles: number;
  pieces: number;
  bundleList: { bundleCode: string; cutCode: string; sizeCode: string; color: string; quantity: number }[];
}

export type ScanStatus =
  | 'ACCEPTED'
  | 'DUPLICATE'
  | 'ALREADY_SCANNED'
  | 'KEY_REUSED'
  | 'INVALID_FORMAT'
  | 'INVALID_SIGNATURE'
  | 'UNKNOWN_TICKET'
  | 'UNKNOWN_OPERATOR'
  | 'INVALID_TIMESTAMP';

export interface ScanResult {
  clientReadingId: string;
  status: ScanStatus;
  message: string;
  readingId?: number;
  ticketId?: string;
  bundleCode?: string;
  operationCode?: string;
  quantity?: number;
  registeredBy?: string;
  registeredAt?: string;
}
