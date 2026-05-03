package com.bogoai.booknow.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Process-wide guard that records active Binance IP bans and rate-limit
 * cool-downs so every scheduled caller can consult one authoritative
 * "is it safe to talk to Binance right now?" check.
 *
 * Why this exists
 * ---------------
 * Without coordination, each {@code @Scheduled} poller (balance, dust,
 * exchange-info, delist, user-data-stream) ignores everyone else's
 * failures. When Binance returns
 *
 *   "Way too much request weight used; IP banned until 1777814809681"
 *
 * the offending poller logs an error, and 60 seconds later the next
 * poller fires the same kind of request — extending the ban every time.
 * Centralising the cool-down stops that feedback loop.
 *
 * Usage pattern
 * -------------
 *   @Scheduled(...)
 *   public void poll() {
 *       if (guard.isBanned()) return;
 *       try {
 *           ... call Binance REST ...
 *       } catch (Exception e) {
 *           if (guard.reportIfBanned(e)) return;
 *           // unrelated error — handle normally
 *       }
 *   }
 */
@Slf4j
@Component
public class BinanceRateLimitGuard {

    /**
     * Matches the embedded epoch-ms in Binance's -1003 error message,
     * e.g. "IP banned until 1777814809681".
     */
    private static final Pattern BAN_UNTIL = Pattern.compile("(?i)banned\\s+until\\s+(\\d{12,})");

    /** Default cool-down when Binance gives a generic 429/418 with no timestamp. */
    private static final long DEFAULT_COOLDOWN_MS = 120_000;

    private final AtomicLong banUntilMs = new AtomicLong(0);

    /** True iff a ban or cool-down is currently active. Cheap to call. */
    public boolean isBanned() {
        return banUntilMs.get() > System.currentTimeMillis();
    }

    /** Seconds remaining on the current cool-down, or 0 if none. */
    public long banRemainingSeconds() {
        long until = banUntilMs.get();
        long now = System.currentTimeMillis();
        return until > now ? (until - now) / 1000 : 0;
    }

    /**
     * Mark a ban explicitly. Used when the caller has parsed the
     * banned-until timestamp itself (e.g. from a WS-API Retry-After header).
     */
    public void recordBanUntil(long epochMs, String context) {
        long current = banUntilMs.get();
        if (epochMs > current) {
            banUntilMs.set(epochMs);
            long secs = (epochMs - System.currentTimeMillis()) / 1000;
            log.error("[RateLimitGuard] Binance IP ban recorded — sleeping {}s ({}). "
                + "All scheduled REST callers will skip until then.", secs, context);
        }
    }

    /**
     * Inspect a Throwable's message chain for Binance's "IP banned until N"
     * marker and, if found, record the ban. Returns true when this exception
     * indicates a ban so callers can branch out without further work.
     *
     * Also recognises the bare phrases "Too much request weight" / "IP auto
     * banned" (Binance variants) and applies a default 120s cool-down.
     */
    public boolean reportIfBanned(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                Matcher m = BAN_UNTIL.matcher(msg);
                if (m.find()) {
                    try {
                        long until = Long.parseLong(m.group(1));
                        recordBanUntil(until, "from Binance error: " + truncate(msg));
                        return true;
                    } catch (NumberFormatException ignored) { /* fall through */ }
                }
                String lower = msg.toLowerCase();
                if (lower.contains("ip banned")
                    || lower.contains("ip auto banned")
                    || lower.contains("too much request weight")
                    || lower.contains("418 i'm a teapot")
                    || lower.contains("teapot")) {
                    recordBanUntil(System.currentTimeMillis() + DEFAULT_COOLDOWN_MS,
                        "default cooldown from message: " + truncate(msg));
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    /** Convenience: clear the ban early (e.g. after a manual probe succeeds). */
    public void clear() {
        banUntilMs.set(0);
        log.info("[RateLimitGuard] Cooldown cleared manually.");
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }
}
