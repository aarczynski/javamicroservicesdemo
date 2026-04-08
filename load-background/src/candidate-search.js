import http from 'k6/http';
import { SharedArray } from 'k6/data';

// Sinusoidal load: oscillates between MIN_RPS and MAX_RPS with the given period.
// Uses ramping-arrival-rate executor so k6 controls the actual request arrival rate —
// no VU synchronization issues, no batching.
const MIN_RPS = 5;
const MAX_RPS = 10;
const PERIOD_S = 300; // sine period: 5 minutes
const VUS = 5;        // pre-allocated VUs; enough for peak RPS with latency headroom

const BASE_RPS = (MIN_RPS + MAX_RPS) / 2;  // 7.5
const AMPLITUDE = (MAX_RPS - MIN_RPS) / 2; // 2.5
const STEP_S = 10;      // rate update granularity; 30 stages per period
const PERIODS = 144;    // run 144 × 5 min = 12 hours before k6 restarts; eliminates visible dips

// Generates PERIODS full sine periods as ramping-arrival-rate stages.
// entrypoint.sh loops the script so k6 restarts every 12 hours instead of every 5 minutes,
// making the inter-run gap invisible on dashboards.
function generateStages() {
    const stages = [];
    for (let p = 0; p < PERIODS; p++) {
        for (let t = 0; t < PERIOD_S; t += STEP_S) {
            const rps = BASE_RPS + AMPLITUDE * Math.sin((2 * Math.PI * t) / PERIOD_S);
            stages.push({ duration: `${STEP_S}s`, target: Math.round(rps) });
        }
    }
    return stages;
}

const candidateIds = new SharedArray('candidate-ids', function () {
    const dataFile = __ENV.DATA_FILE || '/data/01-candidates.sql';
    try {
        return open(dataFile)
            .split('\n')
            .map(line => line.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/))
            .filter(m => m !== null)
            .map(m => m[0]);
    } catch (_) {
        console.warn(`Data file not found: ${dataFile}. Requests will return 404.`);
        return ['00000000-0000-0000-0000-000000000000'];
    }
});

export const options = {
    scenarios: {
        background_load: {
            executor: 'ramping-arrival-rate',
            startRate: Math.round(BASE_RPS),
            timeUnit: '1s',
            preAllocatedVUs: VUS,
            stages: generateStages(),
        },
    },
    // No thresholds — this is background ambient load, not a pass/fail test.
    thresholds: {},
};

export default function () {
    const candidateId = candidateIds[Math.floor(Math.random() * candidateIds.length)];
    const host = __ENV.TARGET_HOST || 'http://app-candidates:8080';

    http.get(`${host}/api/v1/candidates/${candidateId}/matching-offers`, {
        headers: { 'Accept': 'application/json' },
    });
}
