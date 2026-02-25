import axios from "axios";
import {
  DashboardDecisionPageResponse,
  DashboardFilters,
  DashboardMetricsResponse
} from "../types/dashboard";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081"
});

const toIsoOrUndefined = (value: string): string | undefined => {
  if (!value) {
    return undefined;
  }
  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) {
    return undefined;
  }
  return parsedDate.toISOString();
};

const toNumberOrUndefined = (value: string): number | undefined => {
  if (!value.trim()) {
    return undefined;
  }
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : undefined;
};

export const fetchDecisions = async (
  filters: DashboardFilters,
  page: number,
  size: number
): Promise<DashboardDecisionPageResponse> => {
  const response = await api.get<DashboardDecisionPageResponse>("/api/v1/dashboard/decisions", {
    params: {
      userId: filters.userId || undefined,
      decision: filters.decision || undefined,
      minAmount: toNumberOrUndefined(filters.minAmount),
      maxAmount: toNumberOrUndefined(filters.maxAmount),
      from: toIsoOrUndefined(filters.from),
      to: toIsoOrUndefined(filters.to),
      page,
      size
    }
  });
  return response.data;
};

export const fetchMetrics = async (
  from: string,
  to: string
): Promise<DashboardMetricsResponse> => {
  const response = await api.get<DashboardMetricsResponse>("/api/v1/dashboard/metrics", {
    params: {
      from: toIsoOrUndefined(from),
      to: toIsoOrUndefined(to)
    }
  });
  return response.data;
};

export const getStreamUrl = (): string => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";
  return `${baseUrl}/api/v1/dashboard/stream`;
};
