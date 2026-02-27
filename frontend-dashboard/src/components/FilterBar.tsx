import { DashboardFilters, DecisionType } from "../types/dashboard";

interface FilterBarProps {
  filters: DashboardFilters;
  onChange: (nextFilters: DashboardFilters) => void;
  onApply: () => void;
  onReset: () => void;
}

const decisionOptions: Array<{ label: string; value: "" | DecisionType }> = [
  { label: "All Decisions", value: "" },
  { label: "Approved", value: "APPROVED" },
  { label: "Review", value: "REVIEW" },
  { label: "Blocked", value: "BLOCKED" }
];

export const FilterBar = ({ filters, onChange, onApply, onReset }: FilterBarProps) => {
  const updateFilter = <K extends keyof DashboardFilters>(key: K, value: DashboardFilters[K]) => {
    onChange({
      ...filters,
      [key]: value
    });
  };

  return (
    <section className="panel filters-panel">
      <div className="panel-header">
        <h2>Live Filters</h2>
      </div>
      <div className="filter-grid">
        <label>
          User ID
          <input
            type="text"
            placeholder="user-1 (partial match)"
            value={filters.userId}
            onChange={(event) => updateFilter("userId", event.target.value)}
          />
        </label>

        <label>
          Decision
          <select
            value={filters.decision}
            onChange={(event) => updateFilter("decision", event.target.value as DashboardFilters["decision"])}
          >
            {decisionOptions.map((option) => (
              <option key={option.label} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          Min Amount
          <input
            type="number"
            placeholder="0.00"
            value={filters.minAmount}
            onChange={(event) => updateFilter("minAmount", event.target.value)}
          />
        </label>

        <label>
          Max Amount
          <input
            type="number"
            placeholder="5000.00"
            value={filters.maxAmount}
            onChange={(event) => updateFilter("maxAmount", event.target.value)}
          />
        </label>

        <label>
          From
          <input
            type="datetime-local"
            value={filters.from}
            onChange={(event) => updateFilter("from", event.target.value)}
          />
        </label>

        <label>
          To
          <input
            type="datetime-local"
            value={filters.to}
            onChange={(event) => updateFilter("to", event.target.value)}
          />
        </label>
      </div>
      <div className="filter-actions">
        <button className="btn btn-primary" onClick={onApply}>
          Apply
        </button>
        <button className="btn btn-secondary" onClick={onReset}>
          Reset
        </button>
      </div>
    </section>
  );
};
