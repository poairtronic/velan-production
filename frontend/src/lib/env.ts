export interface AppEnv {
  readonly apiBaseUrl: string;
  readonly displayTimezone: 'Asia/Kolkata';
}

function readApiBaseUrl(): string {
  const raw = import.meta.env.VITE_API_BASE_URL?.trim();
  if (!raw) {
    throw new Error(
      'Missing VITE_API_BASE_URL. Set it in frontend/.env or the build environment (e.g. http://localhost:8080/api/v1).'
    );
  }
  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    throw new Error(`Invalid VITE_API_BASE_URL "${raw}": must be a valid http(s) URL.`);
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error(`Invalid VITE_API_BASE_URL "${raw}": protocol must be http: or https:.`);
  }
  return raw;
}

export const env: AppEnv = Object.freeze({
  apiBaseUrl: readApiBaseUrl(),
  displayTimezone: 'Asia/Kolkata',
});
