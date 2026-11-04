# System availability

`tb_system.maintenance` is the source of truth for maintenance mode. The `System`
entity records the start and end of maintenance through its JPA lifecycle rules;
these dates describe the maintenance history, not a scheduled return deadline.
The status API returns these dates with the application's configured time zone.

- `GET /api/system/status` is public and returns `maintenance`, `version`,
  `maintenanceStart`, `maintenanceEnd`, and `timeZone`. It is never cached.
- With no system record, the application stays available.
- While any system record has `maintenance = true`, application requests return
  HTTP 503 with `code = SYSTEM_MAINTENANCE` and `Retry-After: 30`.
- Status, health checks, error dispatch, and OPTIONS requests bypass the
  maintenance filter. Authentication and rate limiting continue to apply.
- The frontend checks status at startup, every 30 seconds, on reconnect/focus,
  and when the user requests another check. It also reacts to the explicit
  maintenance error code. A WeatherAPI/SNIRH outage alone does not activate it.
- Disabling maintenance restores the user's original frontend route without
  clearing their session. Tokens may still expire normally.
- `/404` and unrecognized frontend routes display the 404 page. `/maintenance`
  follows the same entity state; when maintenance is off, it returns to `/home`.

## Configuration and filters

`SecurityConfig` owns the security chain, bucket cache, and servlet-registration
settings. The execution order is rate limiting, maintenance, then JWT
authentication. The servlet registrations are disabled so these filters run
only inside Spring Security's chain.

Filter classes stay separate because each implements a different request rule.
`MaintenanceFilterConfig` and `RateLimitConfig` have been consolidated into
`SecurityConfig`. API clients, caching, asynchronous work, and time zones retain
their own configurations.

There is no public maintenance-toggle endpoint. Change the system record only
through a trusted administrative process. Direct SQL edits do not invoke JPA
lifecycle callbacks, so SQL-based operations must record their timestamps too.
