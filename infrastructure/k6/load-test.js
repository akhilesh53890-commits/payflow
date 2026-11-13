import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '10s', target: 20 },  // Ramp up to 20 users
    { duration: '30s', target: 50 },  // Steady state at 50 users
    { duration: '10s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // 95% of requests under 500ms
    http_req_failed: ['rate<0.01'],                 // Less than 1% failure
  },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/api/v1';
const API_KEY = __ENV.MERCHANT_API_KEY || 'pf_test_merchant_key_12345';

export default function () {
  const idempotencyKey = uuidv4();
  const payload = JSON.stringify({
    amount: (Math.random() * 100 + 10).toFixed(2),
    currency: 'USD',
    paymentMethod: 'CARD',
    customerEmail: `user_${__VU}_${__ITER}@example.com`,
    description: `Load test payment VU ${__VU} Iter ${__ITER}`
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
      'Idempotency-Key': idempotencyKey,
    },
  };

  // 1. Initial payment creation
  const createRes = http.post(`${BASE_URL}/payments`, payload, params);
  check(createRes, {
    'create status is 201 or 200': (r) => r.status === 201 || r.status === 200,
    'paymentId returned': (r) => JSON.parse(r.body).id !== undefined,
  });

  // 2. Immediate identical retry test (Idempotency verification)
  const retryRes = http.post(`${BASE_URL}/payments`, payload, params);
  check(retryRes, {
    'idempotent retry returns identical payment ID': (r) => {
      const orig = JSON.parse(createRes.body);
      const retried = JSON.parse(r.body);
      return orig.id === retried.id;
    },
  });

  sleep(0.5);
}

