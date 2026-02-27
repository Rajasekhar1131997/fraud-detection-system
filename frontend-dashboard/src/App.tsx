import { useCallback, useEffect, useMemo, useState } from "react";
import { fetchDecisions, fetchMetrics, getStreamUrl } from "./api/dashboardApi";
import { ChartsPanel } from "./components/ChartsPanel";
import { DecisionTable } from "./components/DecisionTable";
import { FilterBar } from "./components/FilterBar";
import { MetricsCards } from "./components/MetricsCards";
import {
  DashboardDecision,
  DashboardDecisionPageResponse,
  DashboardFilters,
  DashboardMetricsResponse
} from "./types/dashboard";

const PAGE_SIZE = 20;
const METRICS_REFRESH_INTERVAL_MS = 15_000;

const buildInitialFilters = (): DashboardFilters => {
  return {
    userId: "",
    decision: "",
    minAmount: "",
    maxAmount: "",
    from: "",
    to: ""
  };
};

const isAfter = (left: string, right: string): boolean => {
  if (!left || !right) {
    return false;
  }
  return new Date(left).getTime() > new Date(right).getTime();
};

const decisionMatchesFilters = (decision: DashboardDecision, filters: DashboardFilters): boolean => {
  if (
    filters.userId &&
    !decision.userId.toLowerCase().includes(filters.userId.trim().toLowerCase())
  ) {
    return false;
  }

  if (filters.decision && decision.decision !== filters.decision) {
    return false;
  }

  if (filters.minAmount && (decision.amount ?? 0) < Number(filters.minAmount)) {
    return false;
  }

  if (filters.maxAmount && (decision.amount ?? 0) > Number(filters.maxAmount)) {
    return false;
  }

  const createdAt = new Date(decision.createdAt).getTime();

  if (filters.from && createdAt < new Date(filters.from).getTime()) {
    return false;
  }

  if (filters.to && createdAt > new Date(filters.to).getTime()) {
    return false;
  }

  return true;
};

const sortByCreatedAtDesc = (left: DashboardDecision, right: DashboardDecision): number => {
  return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
};

function App() {
  const [draftFilters, setDraftFilters] = useState<DashboardFilters>(() => buildInitialFilters());
  const [appliedFilters, setAppliedFilters] = useState<DashboardFilters>(() => buildInitialFilters());
  const [page, setPage] = useState(0);

  const [decisionPage, setDecisionPage] = useState<DashboardDecisionPageResponse | null>(null);
  const [metrics, setMetrics] = useState<DashboardMetricsResponse | null>(null);

  const [loadingDecisions, setLoadingDecisions] = useState(false);
  const [loadingMetrics, setLoadingMetrics] = useState(false);

  const [decisionsError, setDecisionsError] = useState<string | null>(null);
  const [metricsError, setMetricsError] = useState<string | null>(null);

  const [streamState, setStreamState] = useState<"connecting" | "connected" | "disconnected">("connecting");

  const filtersAreInvalid = useMemo(
    () => isAfter(draftFilters.from, draftFilters.to),
    [draftFilters.from, draftFilters.to]
  );

  const loadDecisions = useCallback(async () => {
    setLoadingDecisions(true);
    setDecisionsError(null);
    try {
      const nextDecisionPage = await fetchDecisions(appliedFilters, page, PAGE_SIZE);
      setDecisionPage(nextDecisionPage);
    } catch (error) {
      setDecisionsError("Unable to load transaction decisions.");
    } finally {
      setLoadingDecisions(false);
    }
  }, [appliedFilters, page]);

  const loadMetrics = useCallback(async () => {
    setLoadingMetrics(true);
    setMetricsError(null);
    try {
      const nextMetrics = await fetchMetrics(appliedFilters.from, appliedFilters.to);
      setMetrics(nextMetrics);
    } catch (error) {
      setMetricsError("Unable to load risk metrics.");
    } finally {
      setLoadingMetrics(false);
    }
  }, [appliedFilters.from, appliedFilters.to]);

  useEffect(() => {
    void loadDecisions();
  }, [loadDecisions]);

  useEffect(() => {
    void loadMetrics();
    const intervalId = window.setInterval(() => {
      void loadMetrics();
    }, METRICS_REFRESH_INTERVAL_MS);
    return () => window.clearInterval(intervalId);
  }, [loadMetrics]);

  useEffect(() => {
    let eventSource: EventSource | null = null;
    let isDisposed = false;
    setStreamState("connecting");

    const connectStream = async () => {
      try {
        const streamUrl = await getStreamUrl();
        if (isDisposed) {
          return;
        }

        eventSource = new EventSource(streamUrl);

        eventSource.addEventListener("connected", () => {
          setStreamState("connected");
        });

        eventSource.addEventListener("decision", (event: MessageEvent) => {
          try {
            const nextDecision = JSON.parse(event.data) as DashboardDecision;
            if (page !== 0 || !decisionMatchesFilters(nextDecision, appliedFilters)) {
              return;
            }

            setDecisionPage((previousPage) => {
              if (!previousPage) {
                return previousPage;
              }

              const withoutDuplicate = previousPage.content.filter(
                (existingDecision) =>
                  !(
                    existingDecision.transactionId === nextDecision.transactionId &&
                    existingDecision.createdAt === nextDecision.createdAt
                  )
              );

              const updatedContent = [nextDecision, ...withoutDuplicate]
                .sort(sortByCreatedAtDesc)
                .slice(0, previousPage.size);

              return {
                ...previousPage,
                content: updatedContent,
                totalElements: previousPage.totalElements + 1
              };
            });

            void loadMetrics();
          } catch (error) {
            setStreamState("disconnected");
          }
        });

        eventSource.onerror = () => {
          setStreamState("disconnected");
        };
      } catch (error) {
        setStreamState("disconnected");
      }
    };

    void connectStream();

    return () => {
      isDisposed = true;
      eventSource?.close();
    };
  }, [appliedFilters, loadMetrics, page]);

  const applyFilters = () => {
    if (filtersAreInvalid) {
      return;
    }
    setPage(0);
    setAppliedFilters(draftFilters);
  };

  const resetFilters = () => {
    const initialFilters = buildInitialFilters();
    setDraftFilters(initialFilters);
    setAppliedFilters(initialFilters);
    setPage(0);
  };

  return (
    <main className="app-shell">
      <section className="hero">
        <div>
          <p className="eyebrow">Week 4 Dashboard</p>
          <h1>Real-Time Fraud Monitoring Console</h1>
          <p>
            Streaming fraud decisions, risk analytics, and production telemetry for the hybrid
            fraud engine.
          </p>
        </div>
        <div className={`stream-indicator stream-${streamState}`}>
          Stream: {streamState.toUpperCase()}
        </div>
      </section>

      <FilterBar
        filters={draftFilters}
        onChange={setDraftFilters}
        onApply={applyFilters}
        onReset={resetFilters}
      />

      {filtersAreInvalid ? (
        <p className="error-banner">Invalid range: the "From" date must be earlier than "To".</p>
      ) : null}

      {metricsError ? <p className="error-banner">{metricsError}</p> : null}
      <MetricsCards metrics={metrics} loading={loadingMetrics} />
      <ChartsPanel metrics={metrics} />

      <DecisionTable
        decisionPage={decisionPage}
        loading={loadingDecisions}
        error={decisionsError}
        onPrevious={() => setPage((currentPage) => Math.max(0, currentPage - 1))}
        onNext={() => {
          if (decisionPage?.last) {
            return;
          }
          setPage((currentPage) => currentPage + 1);
        }}
      />
    </main>
  );
}

export default App;
