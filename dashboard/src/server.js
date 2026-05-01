/**
 * src/server.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Application entry point.
 *
 * Responsibilities (and ONLY these — everything else is in other modules):
 *   1. Create Express app + HTTP server + Socket.io server
 *   2. Mount middleware (JSON parser, static files)
 *   3. Inject req.io so route handlers can emit socket events
 *   4. Mount the REST API router
 *   5. Start the Redis connection
 *   6. Start the Socket.io broadcaster loop
 *   7. Start listening on PORT
 *
 * No business logic lives here.
 */

const express      = require('express');
const http         = require('http');
const { Server }   = require('socket.io');
const path         = require('path');

const { PORT }     = require('./config/settings');
const { client }   = require('./redis/client');
const apiRouter    = require('./routes/api');
const broadcaster  = require('./sockets/broadcaster');

// ── Create servers ────────────────────────────────────────────────────────────
const app    = express();
const server = http.createServer(app);
const io     = new Server(server, {
    cors: { origin: '*' },    // Allow any origin (lock down in production)
});

// ── Middleware ────────────────────────────────────────────────────────────────

// Parse JSON request bodies
app.use(express.json());

// Serve the front-end from /public
app.use(express.static(path.join(__dirname, '..', 'public')));

/**
 * Inject the Socket.io instance into every request object.
 * This lets route handlers call req.io.emit() without importing the io instance.
 */
app.use((req, _res, next) => {
    req.io = io;
    next();
});

// ── Routes ────────────────────────────────────────────────────────────────────
app.use('/api', apiRouter);

// ── Start services ────────────────────────────────────────────────────────────

// Connect to Redis (errors are logged inside the client module)
client.connect().catch(() => {});

// Start the polling/broadcast loop
broadcaster.init(io);

// Start HTTP server
server.listen(PORT, () => {
    console.log('\n─────────────────────────────────────────');
    console.log(`🚀  BookNow Dashboard → http://localhost:${PORT}`);
    console.log('─────────────────────────────────────────\n');
});
