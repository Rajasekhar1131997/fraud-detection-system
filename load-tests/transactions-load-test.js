import http from "k6/http";
import { check, fail, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const authUsername = __ENV.AUTH_USERNAME || "analyst";
const authPassword = __ENV.AUTH_PASSWORD || "analyst-change-me";

export const options = {
  scenarios: {
    steady_state: {
      executor: "constant-arrival-rate",
      rate: 10,
      timeUnit: "1s",
      duration: "4m",
      preAllocatedVUs: 20,
      maxVUs: 80
    },
    high_risk_burst: {
      executor: "constant-arrival-rate",
      rate: 18,
      timeUnit: "1s",
      duration: "1m",
      startTime: "1m",
      preAllocatedVUs: 30,
      maxVUs: 120
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1200"]
  }
};

const merchants = ["merchant-1", "merchant-2", "crypto-exchange-1", "luxury-retail-9"];
const locations = ["New York, US", "Austin, US", "Moscow, RU", "Lagos, NG", "Berlin, DE"];

function randomId(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}

function randomItem(items) {
  return items[Math.floor(Math.random() * items.length)];
}

function randomAmount() {
  const highRiskBias = Math.random() < 0.2;
  if (highRiskBias) {
    return (8000 + Math.random() * 12000).toFixed(2);
  }
  return (10 + Math.random() * 1200).toFixed(2);
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
      }
    }
  );

  const ok = check(response, {
    "auth status is 200": (result) => result.status === 200
  });

  if (!ok) {
    fail(`Unable to authenticate for load test at ${baseUrl}/api/v1/auth/token`);
  }

  const payload = response.json();
  return {
    accessToken: payload.accessToken
  };
}

export default function (setupData) {
  const payload = JSON.stringify({
    transactionId: randomId("txn"),
    userId: randomId("user"),
    amount: Number(randomAmount()),
    currency: "USD",
    merchantId: randomItem(merchants),
    location: randomItem(locations),
    deviceId: randomId("device")
  });

  const response = http.post(`${baseUrl}/api/v1/transactions`, payload, {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${setupData.accessToken}`
    }
  });

  check(response, {
    "transaction accepted": (result) => result.status === 201
  });

  sleep(0.2);
}
