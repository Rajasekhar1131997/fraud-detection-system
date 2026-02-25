import { DashboardMetricsResponse } from "../types/dashboard";

interface MetricsCardsProps {
  metrics: DashboardMetricsResponse | null;
  loading: boolean;
}

const renderMetricValue = (loading: boolean, value: string): string => {
  if (loading) {
    return "Loading...";
  }
  return value;
};

export const MetricsCards = ({ metrics, loading }: MetricsCardsProps) => {
  return (
    <section className="metrics-grid">
      <article className="panel metric-card">
        <h3>Total Transactions</h3>
        <p>{renderMetricValue(loading, `${metrics?.totalTransactions ?? 0}`)}</p>
      </article>
      <article className="panel metric-card">
        <h3>Fraud Rate</h3>
        <p>{renderMetricValue(loading, `${metrics?.fraudRatePercentage ?? 0}%`)}</p>
      </article>
      <article className="panel metric-card">
        <h3>Avg Risk Score</h3>
        <p>{renderMetricValue(loading, `${metrics?.averageRiskScore ?? 0}`)}</p>
      </article>
      <article className="panel metric-card">
        <h3>Approved / Blocked</h3>
        <p>
          {renderMetricValue(
            loading,
            `${metrics?.approvedCount ?? 0} / ${metrics?.blockedCount ?? 0}`
          )}
        </p>
      </article>
    </section>
  );
};
