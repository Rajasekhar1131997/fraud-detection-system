import axios, { AxiosHeaders } from "axios";
import {
  DashboardDecisionPageResponse,
  DashboardFilters,
  DashboardMetricsResponse
} from "../types/dashboard";

interface AuthTokenResponse {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
  roles: string[];
}

const browserHostname = typeof window !== "undefined" ? window.location.hostname : "localhost";
const defaultBaseURL =
  browserHostname === "localhost" || browserHostname === "127.0.0.1"
    ? "http://localhost:8081"
    : typeof window !== "undefined"
      ? window.location.origin
      : "http://localhost:8081";

const baseURL = import.meta.env.VITE_API_BASE_URL ?? defaultBaseURL;
const authUsername = import.meta.env.VITE_DASHBOARD_USERNAME ?? "analyst";
const authPassword = import.meta.env.VITE_DASHBOARD_PASSWORD ?? "analyst-change-me";
const TOKEN_EXPIRY_SKEW_MS = 5_000;

const api = axios.create({
  baseURL
});

const authApi = axios.create({
  baseURL
});

let cachedAccessToken: string | null = null;
let cachedTokenExpiryMs = 0;
let tokenRequestInFlight: Promise<string> | null = null;

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

const getValidAccessToken = async (): Promise<string> => {
  const now = Date.now();
  if (cachedAccessToken && now + TOKEN_EXPIRY_SKEW_MS < cachedTokenExpiryMs) {
    return cachedAccessToken;
  }

  if (tokenRequestInFlight) {
    return tokenRequestInFlight;
  }

  tokenRequestInFlight = authApi
    .post<AuthTokenResponse>("/api/v1/auth/token", {
      username: authUsername,
      password: authPassword
    })
    .then(({ data }) => {
      cachedAccessToken = data.accessToken;
      const parsedExpiry = Date.parse(data.expiresAt);
      cachedTokenExpiryMs = Number.isFinite(parsedExpiry)
        ? parsedExpiry
        : Date.now() + 55 * 60 * 1_000;
      return data.accessToken;
    })
    .finally(() => {
      tokenRequestInFlight = null;
    });

  return tokenRequestInFlight;
};

api.interceptors.request.use(async (config) => {
  if (config.url?.startsWith("/api/v1/auth/")) {
    return config;
  }

  const accessToken = await getValidAccessToken();
  if (!config.headers) {
    config.headers = new AxiosHeaders();
  }

  config.headers.set("Authorization", `Bearer ${accessToken}`);
  return config;
});

export const fetchDecisions = async (
  filters: DashboardFilters,
  page: number,
  size: number
): Promise<DashboardDecisionPageResponse> => {
  const normalizedUserId = filters.userId.trim();
  const response = await api.get<DashboardDecisionPageResponse>("/api/v1/dashboard/decisions", {
    params: {
      userId: normalizedUserId || undefined,
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

export const getStreamUrl = async (): Promise<string> => {
  const accessToken = await getValidAccessToken();
  return `${baseURL}/api/v1/dashboard/stream?access_token=${encodeURIComponent(accessToken)}`;
};
