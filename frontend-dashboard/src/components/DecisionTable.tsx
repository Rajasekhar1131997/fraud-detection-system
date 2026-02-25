import { DashboardDecisionPageResponse } from "../types/dashboard";

interface DecisionTableProps {
  decisionPage: DashboardDecisionPageResponse | null;
  loading: boolean;
  error: string | null;
  onPrevious: () => void;
  onNext: () => void;
}

const formatDate = (value: string): string => {
  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) {
    return value;
  }
  return parsedDate.toLocaleString();
};

const decisionClass = (decision: string): string => {
  return `decision-pill decision-${decision.toLowerCase()}`;
};

export const DecisionTable = ({
  decisionPage,
  loading,
  error,
  onPrevious,
  onNext
}: DecisionTableProps) => {
  return (
    <section className="panel table-panel">
      <div className="panel-header">
        <h2>Live Transaction Decisions</h2>
      </div>

      {error ? <p className="error-banner">{error}</p> : null}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Txn ID</th>
              <th>User</th>
              <th>Amount</th>
              <th>Decision</th>
              <th>Risk</th>
              <th>Rule</th>
              <th>ML</th>
              <th>Location</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={9}>Loading decisions...</td>
              </tr>
            ) : null}
            {!loading && (decisionPage?.content.length ?? 0) === 0 ? (
              <tr>
                <td colSpan={9}>No decisions found for current filters.</td>
              </tr>
            ) : null}
            {!loading &&
              decisionPage?.content.map((decision) => (
                <tr key={`${decision.transactionId}-${decision.createdAt}`}>
                  <td>{formatDate(decision.createdAt)}</td>
                  <td>{decision.transactionId}</td>
                  <td>{decision.userId}</td>
                  <td>
                    {decision.amount ?? 0} {decision.currency ?? ""}
                  </td>
                  <td>
                    <span className={decisionClass(decision.decision)}>{decision.decision}</span>
                  </td>
                  <td>{decision.riskScore}</td>
                  <td>{decision.ruleScore}</td>
                  <td>{decision.mlScore}</td>
                  <td>{decision.location ?? "-"}</td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      <div className="table-footer">
        <span>
          Page {(decisionPage?.page ?? 0) + 1} of {Math.max(1, decisionPage?.totalPages ?? 1)}
        </span>
        <div className="pagination-buttons">
          <button
            className="btn btn-secondary"
            onClick={onPrevious}
            disabled={!decisionPage || decisionPage.page === 0}
          >
            Previous
          </button>
          <button
            className="btn btn-secondary"
            onClick={onNext}
            disabled={!decisionPage || decisionPage.last}
          >
            Next
          </button>
        </div>
      </div>
    </section>
  );
};
