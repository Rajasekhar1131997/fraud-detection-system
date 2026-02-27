import http from "k6/http";
import { check, fail } from "k6";
import { Counter, Rate } from "k6/metrics";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const authUsername = __ENV.AUTH_USERNAME || "analyst";
const authPassword = __ENV.AUTH_PASSWORD || "analyst-change-me";
const profileName = __ENV.PROFILE || "capacity_5m_day";

const capacityProfiles = {
  capacity_1m_day: {
    description: "Baseline sustained load (~1M transactions/day => ~12 rps).",
    scenarios: {
      sustained: {
        executor: "constant-arrival-rate",
        rate: 12,
        timeUnit: "1s",
        duration: "20m",
        preAllocatedVUs: 30,
        maxVUs: 200
      }
    }
  },
  capacity_5m_day: {
    description: "Target sustained load (~5M transactions/day => ~58 rps).",
    scenarios: {
      sustained: {
        executor: "constant-arrival-rate",
        rate: 58,
        timeUnit: "1s",
        duration: "30m",
        preAllocatedVUs: 120,
        maxVUs: 500
      }
    }
  },
  capacity_10m_day: {
    description: "Stretch sustained load (~10M transactions/day => ~116 rps).",
    scenarios: {
      sustained: {
        executor: "constant-arrival-rate",
        rate: 116,
        timeUnit: "1s",
        duration: "30m",
        preAllocatedVUs: 220,
        maxVUs: 900
      }
    }
  },
  spike_10m_day_peak: {
    description: "Burst profile with peak above 10M/day equivalent traffic.",
    scenarios: {
      spike: {
        executor: "ramping-arrival-rate",
        startRate: 30,
        timeUnit: "1s",
        preAllocatedVUs: 220,
        maxVUs: 1000,
        stages: [
          { target: 60, duration: "5m" },
          { target: 180, duration: "10m" },
          { target: 60, duration: "5m" }
        ]
      }
    }
  }
};

if (!capacityProfiles[profileName]) {
  fail(
    `Unknown PROFILE=${profileName}. Valid profiles: ${Object.keys(capacityProfiles).join(", ")}`
  );
}

const transactionAcceptedCounter = new Counter("transaction_accepted_total");
const transactionRejectedCounter = new Counter("transaction_rejected_total");
const transactionAcceptedRate = new Rate("transaction_accepted_rate");

export const options = {
  scenarios: capacityProfiles[profileName].scenarios,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1200", "p(99)<2000"],
    transaction_accepted_rate: ["rate>0.99"]
  },
  tags: {
    test_type: "capacity",
    profile: profileName
  }
};

const merchants = ["merchant-1", "merchant-2", "crypto-exchange-1", "luxury-retail-9"];
const locations = ["New York, US", "Austin, US", "Moscow, RU", "Lagos, NG", "Berlin, DE"];

function randomId(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
}

function randomItem(items) {
  return items[Math.floor(Math.random() * items.length)];
}

function randomAmount() {
  const highRiskBias = Math.random() < 0.2;
  if (highRiskBias) {
    return Number((8000 + Math.random() * 12000).toFixed(2));
  }
  return Number((10 + Math.random() * 1200).toFixed(2));
}

function randomUserId() {
  const shard = Math.floor(Math.random() * 25000);
  return `user-${shard}`;
}

export function setup() {
  const response = http.post(
    `${baseUrl}/api/v1/auth/token`,
    JSON.stringify({
      username: authUsername,
      password: authPassword
    }),
    {
      headers: {
        "Content-Type": "application/json"
      },
      tags: {
        endpoint: "auth"
      }
    }
  );

  const success = check(response, {
    "auth status is 200": (result) => result.status === 200,
    "auth response has token": (result) => {
      const payload = result.json();
      return Boolean(payload && payload.accessToken);
    }
  });

  if (!success) {
    fail(`Failed to obtain JWT token from ${baseUrl}/api/v1/auth/token`);
  }

  const payload = response.json();
  return { accessToken: payload.accessToken };
}

export default function (setupData) {
  const payload = JSON.stringify({
    transactionId: randomId("txn"),
    userId: randomUserId(),
    amount: randomAmount(),
    currency: "USD",
    merchantId: randomItem(merchants),
    location: randomItem(locations),
    deviceId: randomId("device")
  });

  const response = http.post(`${baseUrl}/api/v1/transactions`, payload, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${setupData.accessToken}`
    },
    tags: {
      endpoint: "ingestion"
    }
  });

  const accepted = check(response, {
    "transaction accepted (201)": (result) => result.status === 201
  });
  transactionAcceptedRate.add(accepted);

  if (accepted) {
    transactionAcceptedCounter.add(1);
  } else {
    transactionRejectedCounter.add(1);
  }
}

export function handleSummary(data) {
  const summary = {
    profile: profileName,
    description: capacityProfiles[profileName].description,
    metrics: {
      checks_pass_rate: data.metrics.checks ? data.metrics.checks.values.rate : null,
      http_failed_rate: data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : null,
      http_p95_ms: data.metrics.http_req_duration
        ? data.metrics.http_req_duration.values["p(95)"]
        : null,
      http_p99_ms: data.metrics.http_req_duration
        ? data.metrics.http_req_duration.values["p(99)"]
        : null,
      accepted_rate: data.metrics.transaction_accepted_rate
        ? data.metrics.transaction_accepted_rate.values.rate
        : null,
      accepted_total: data.metrics.transaction_accepted_total
        ? data.metrics.transaction_accepted_total.values.count
        : null
    }
  };

  return {
    stdout: `${JSON.stringify(summary, null, 2)}\n`
  };
}
