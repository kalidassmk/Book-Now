/**
 * src/services/springService.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Manages the Spring Boot trading bot process lifecycle.
 *
 * Responsibilities:
 *   - start()  → spawn `mvn spring-boot:run` in the Spring Boot project dir
 *   - stop()   → kill the managed process (SIGTERM → SIGKILL fallback)
 *   - status() → return { running, pid, startedAt }
 *
 * The spawned process's stdout/stderr is piped to this process so Spring Boot
 * logs appear in the Node console alongside dashboard logs.
 */

const { spawn }  = require('child_process');
const path       = require('path');

// ── Configuration ─────────────────────────────────────────────────────────────

// Absolute path to the Spring Boot Maven project
const SPRING_DIR = path.resolve(__dirname, '../../../book-now-v3');

// Command to start Spring Boot
const MVN_CMD  = 'mvn';
const MVN_ARGS = ['spring-boot:run'];

// ── State ─────────────────────────────────────────────────────────────────────

let _proc      = null;   // ChildProcess | null
let _startedAt = null;   // Date | null
let _stopping  = false;  // prevent double-kill

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Start the Spring Boot service.
 * Resolves with { ok, message, pid }.
 * Rejects if the service is already running.
 */
function start() {
    return new Promise((resolve, reject) => {
        if (_proc && !_proc.killed) {
            return reject(new Error('Spring Boot is already running (pid ' + _proc.pid + ')'));
        }

        console.log('[SpringService] Starting Spring Boot → mvn spring-boot:run');
        console.log('[SpringService] Working dir:', SPRING_DIR);

        _stopping = false;
        _proc = spawn(MVN_CMD, MVN_ARGS, {
            cwd:   SPRING_DIR,
            stdio: ['ignore', 'pipe', 'pipe'],
            // inherit the shell env so JAVA_HOME / PATH are available
            env:   { ...process.env },
        });

        _startedAt = new Date();

        // Pipe Spring Boot stdout → Node stdout with a prefix
        _proc.stdout.on('data', (chunk) => {
            process.stdout.write('[SpringBoot] ' + chunk.toString());
        });
        _proc.stderr.on('data', (chunk) => {
            process.stderr.write('[SpringBoot] ' + chunk.toString());
        });

        _proc.on('error', (err) => {
            console.error('[SpringService] Failed to spawn process:', err.message);
            _proc = null; _startedAt = null;
        });

        _proc.on('exit', (code, signal) => {
            if (!_stopping) {
                console.warn(`[SpringService] Process exited unexpectedly (code=${code} signal=${signal})`);
            } else {
                console.log(`[SpringService] Process stopped (code=${code} signal=${signal})`);
            }
            _proc = null; _startedAt = null; _stopping = false;
        });

        // Give the process a tick to fail fast (bad command, bad cwd, etc.)
        setTimeout(() => {
            if (_proc && !_proc.killed) {
                resolve({ ok: true, message: 'Spring Boot started', pid: _proc.pid });
            } else {
                reject(new Error('Spring Boot process failed to start. Check that mvn is in PATH and book-now-v3 exists.'));
            }
        }, 600);
    });
}

/**
 * Stop the Spring Boot service.
 * Resolves with { ok, message }.
 */
function stop() {
    return new Promise((resolve, reject) => {
        if (!_proc || _proc.killed) {
            return reject(new Error('Spring Boot is not running'));
        }

        console.log('[SpringService] Stopping Spring Boot (pid ' + _proc.pid + ')…');
        _stopping = true;

        // Give it 5 seconds to die gracefully before SIGKILL
        const timer = setTimeout(() => {
            if (_proc && !_proc.killed) {
                console.warn('[SpringService] SIGTERM timed out — sending SIGKILL');
                _proc.kill('SIGKILL');
            }
        }, 5000);

        _proc.once('exit', () => {
            clearTimeout(timer);
            resolve({ ok: true, message: 'Spring Boot stopped' });
        });

        // Kill the entire process group so child threads (mvn wrapper) also die
        try {
            process.kill(-_proc.pid, 'SIGTERM');
        } catch {
            _proc.kill('SIGTERM');
        }
    });
}

/**
 * Return the current service status.
 * @returns {{ running: boolean, pid: number|null, startedAt: string|null }}
 */
function status() {
    const running = !!(_proc && !_proc.killed);
    return {
        running,
        pid:       running ? _proc.pid  : null,
        startedAt: running ? _startedAt.toISOString() : null,
    };
}

module.exports = { start, stop, status };
