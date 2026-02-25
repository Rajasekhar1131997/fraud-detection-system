import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts";
import { DashboardMetricsResponse } from "../types/dashboard";

interface ChartsPanelProps {
  metrics: DashboardMetricsResponse | null;
}

const formatMinute = (minute: string): string => {
  const parsedDate = new Date(minute);
  if (Number.isNaN(parsedDate.getTime())) {
    return minute;
  }
  return parsedDate.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
};

export const ChartsPanel = ({ metrics }: ChartsPanelProps) => {
  const throughputData = metrics?.transactionsPerMinute.map((point) => ({
    minute: formatMinute(point.minute),
    count: point.count
  })) ?? [{ minute: "No Data", count: 0 }];

  const decisionDistributionData = [
    { name: "Approved", value: metrics?.approvedCount ?? 0, color: "#0f9d8a" },
    { name: "Review", value: metrics?.reviewCount ?? 0, color: "#ff9f43" },
    { name: "Blocked", value: metrics?.blockedCount ?? 0, color: "#ff4f5e" }
  ];

  const riskAndFraudData = [
    {
      name: "Metrics",
      averageRiskScore: metrics?.averageRiskScore ?? 0,
      fraudRatePercentage: metrics?.fraudRatePercentage ?? 0
    }
  ];

  return (
    <section className="charts-grid">
      <article className="panel chart-card">
        <h3>Transactions per Minute</h3>
        <div className="chart-container">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={throughputData}>
              <CartesianGrid strokeDasharray="4 4" stroke="#3f5460" />
              <XAxis dataKey="minute" stroke="#c5d5db" />
              <YAxis stroke="#c5d5db" />
              <Tooltip />
              <Line type="monotone" dataKey="count" stroke="#00d4ff" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </article>

      <article className="panel chart-card">
        <h3>Decision Distribution</h3>
        <div className="chart-container">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Tooltip />
              <Legend />
              <Pie
                data={decisionDistributionData}
                dataKey="value"
                nameKey="name"
                outerRadius={88}
                label
              />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </article>

      <article className="panel chart-card">
        <h3>Risk vs Fraud Rate</h3>
        <div className="chart-container">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={riskAndFraudData}>
              <CartesianGrid strokeDasharray="4 4" stroke="#3f5460" />
              <XAxis dataKey="name" stroke="#c5d5db" />
              <YAxis stroke="#c5d5db" />
              <Tooltip />
              <Legend />
              <Bar dataKey="averageRiskScore" fill="#00d4ff" />
              <Bar dataKey="fraudRatePercentage" fill="#ff9f43" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </article>
    </section>
  );
};
