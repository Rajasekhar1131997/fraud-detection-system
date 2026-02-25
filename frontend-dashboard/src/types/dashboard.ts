export type DecisionType = "APPROVED" | "REVIEW" | "BLOCKED";

export interface DashboardDecision {
  transactionId: string;
  userId: string;
  amount: number | null;
  currency: string | null;
  merchantId: string | null;
  location: string | null;
  riskScore: number;
  decision: DecisionType;
  ruleScore: number;
  mlScore: number;
  createdAt: string;
}

export interface DashboardDecisionPageResponse {
  content: DashboardDecision[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface TransactionsPerMinutePoint {
  minute: string;
  count: number;
}

export interface DashboardMetricsResponse {
  from: string;
  to: string;
  totalTransactions: number;
  approvedCount: number;
  reviewCount: number;
  blockedCount: number;
  fraudRatePercentage: number;
  averageRiskScore: number;
  transactionsPerMinute: TransactionsPerMinutePoint[];
}

export interface DashboardFilters {
  userId: string;
  decision: "" | DecisionType;
  minAmount: string;
  maxAmount: string;
  from: string;
  to: string;
}
