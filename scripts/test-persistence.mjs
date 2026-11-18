import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { access, mkdtemp, open, writeFile } from 'node:fs/promises';
import net from 'node:net';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { MongoClient } from 'mongodb';

const root = fileURLToPath(new URL('../', import.meta.url));
const jar = join(root, 'backend/target/smartbiz-backend-0.0.1-SNAPSHOT.jar');
const binary = process.env.MONGOD_BINARY || 'mongod';
const uri = 'mongodb://127.0.0.1:27018/persistence_test?replicaSet=persist0';
const base = 'http://127.0.0.1:8081/api';
const children = new Set();
let directory;
let cookie = '';
let interrupted = false;

async function freePort(port) {
  const server = net.createServer();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen({ port, host: '::', exclusive: true }, resolve);
  });
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
}

async function start(command, args, log, env = process.env) {
  const output = await open(join(directory, log), 'a');
  try {
    const child = spawn(command, args, { cwd: root, env, stdio: ['ignore', output.fd, output.fd] });
    const entry = { child, done: false, error: null };
    children.add(entry);
    entry.closed = new Promise(resolve => {
      child.once('error', error => { entry.error = error; });
      child.once('close', (code, signal) => {
        entry.done = true;
        entry.result = `${command}: code=${code}, signal=${signal}`;
        resolve();
      });
    });
    return entry;
  } finally {
    await output.close();
  }
}

async function stop(entry) {
  if (!entry || entry.done) return;
  entry.child.kill('SIGTERM');
  const deadline = Date.now() + 30000;
  while (!entry.done && Date.now() < deadline) await delay(100);
  if (!entry.done) {
    entry.child.kill('SIGKILL');
    await entry.closed;
    throw new Error('El proceso propio no termino con SIGTERM');
  }
  assert.equal(entry.error, null);
  // The JVM reports SIGTERM as exit status 128 + 15 after its shutdown hooks.
  assert.ok(entry.child.exitCode === 0 || entry.child.exitCode === 143
    || entry.child.signalCode === 'SIGTERM', entry.result);
}

async function waitFor(label, entry, operation) {
  const deadline = Date.now() + 90000;
  let last;
  while (Date.now() < deadline) {
    if (interrupted) throw new Error('Test interrumpido');
    if (entry.done) throw entry.error || new Error(entry.result);
    try { return await operation(); } catch (error) { last = error; }
    await delay(300);
  }
  throw new Error(`Timeout esperando ${label}: ${last?.message}`);
}

async function request(path, method = 'GET', body, expected = 200) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: { Origin: 'http://localhost:5173', 'Content-Type': 'application/json', Cookie: cookie },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(3000),
  });
  const text = await response.text();
  assert.equal(response.status, expected, `${method} ${path}: ${text}`);
  const session = response.headers.get('set-cookie');
  if (session) cookie = session.split(';')[0];
  return text ? JSON.parse(text) : null;
}

async function mongoStart(round, initiate) {
  await freePort(27018);
  const entry = await start(binary, [
    '--dbpath', directory, '--port', '27018', '--bind_ip', '127.0.0.1', '--replSet', 'persist0',
  ], `mongo-${round}.log`);
  const client = new MongoClient('mongodb://127.0.0.1:27018/?directConnection=true', {
    serverSelectionTimeoutMS: 1000, connectTimeoutMS: 1000, socketTimeoutMS: 2000,
  });
  try {
    await waitFor('MongoDB', entry, async () => {
      await client.connect();
      return client.db('admin').command({ ping: 1 });
    });
    if (initiate) await client.db('admin').command({
      replSetInitiate: { _id: 'persist0', members: [{ _id: 0, host: '127.0.0.1:27018' }] },
    });
    await waitFor('PRIMARY', entry, async () => {
      const hello = await client.db('admin').command({ hello: 1 });
      assert.equal(hello.setName, 'persist0');
      assert.equal(hello.isWritablePrimary, true);
    });
  } finally { await client.close(); }
  return entry;
}

async function backendStart(round) {
  await freePort(8081);
  const entry = await start('java', ['-jar', jar], `backend-${round}.log`, {
    ...process.env, PORT: '8081', MONGODB_URI: uri, SEED_DEMO: 'true',
    ALLOWED_ORIGINS: 'http://localhost:5173', COOKIE_SECURE: 'false',
  });
  await waitFor('backend y admin seed', entry, () => request('/auth/login', 'POST', {
    email: 'admin@smartbiz.local', password: 'DemoSmartBiz2026!',
  }));
  return entry;
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    interrupted = true;
    for (const entry of children) if (!entry.done) entry.child.kill('SIGTERM');
  });
}

try {
  if (binary.includes('/')) await access(binary);
  await access(jar);
  await freePort(27018);
  await freePort(8081);
  directory = await mkdtemp(join(tmpdir(), 'smartbiz-persistence-'));
  console.log(`Evidencia y disco MongoDB: ${directory}`);
  let mongo = await mongoStart(1, true);
  let backend = await backendStart(1);
  const admin = await request('/auth/me');
  assert.equal(admin.role, 'ADMIN');
  const category = await request('/categories', 'POST', { name: 'Persistence test' }, 201);
  const product = await request('/products', 'POST', {
    code: 'PERSIST-001', name: 'Persistence product', categoryId: category.id, price: 1500, stock: 10,
  }, 201);
  const sale = await request('/sales', 'POST', {
    items: [{ productId: product.id, quantity: 2 }], tendered: 5000,
  }, 201);
  assert.ok(category.id && product.id && sale.id);
  await writeFile(join(directory, 'ids.json'), JSON.stringify({ categoryId: category.id, productId: product.id, saleId: sale.id }, null, 2));
  const previousCookie = cookie;
  await stop(backend);
  await stop(mongo);
  console.log('Backend y MongoDB detenidos con SIGTERM; reiniciando sobre el mismo disco.');
  mongo = await mongoStart(2, false);
  cookie = '';
  backend = await backendStart(2);
  const newCookie = cookie;
  cookie = previousCookie;
  await request('/auth/me', 'GET', undefined, 401);
  cookie = newCookie;
  assert.equal((await request('/auth/me')).id, admin.id);
  assert.deepEqual((await request('/categories')).find(item => item.id === category.id), category);
  const persistedProduct = (await request('/products')).find(item => item.id === product.id);
  assert.deepEqual(persistedProduct, { ...product, stock: 8 });
  const persistedSale = (await request('/sales')).find(item => item.id === sale.id);
  // BSON dates preserve milliseconds, not Java Instant nanoseconds.
  assert.deepEqual(persistedSale, { ...sale, createdAt: new Date(sale.createdAt).toISOString() });
  assert.equal(persistedSale.total, 3000);
  assert.equal(persistedSale.tendered, 5000);
  assert.equal(persistedSale.change, 2000);
  assert.equal(persistedSale.paymentMethod, 'CASH');
  assert.deepEqual(persistedSale.items, [{
    productId: product.id, code: product.code, name: product.name, quantity: 2, unitPrice: 1500, subtotal: 3000,
  }]);
  const client = new MongoClient(uri, { serverSelectionTimeoutMS: 5000 });
  let movements;
  try {
    movements = await client.db().collection('movements').find({ saleId: sale.id }).toArray();
    assert.equal(movements.length, 1);
    assert.equal(movements[0].productId, product.id);
    assert.equal(movements[0].quantity, -2);
    assert.equal(movements[0].authorId, admin.id);
    assert.equal(movements[0].createdAt.toISOString(), persistedSale.createdAt);
  } finally { await client.close(); }
  await writeFile(join(directory, 'result.json'), JSON.stringify({
    passed: true, uri, category, product: persistedProduct, sale: persistedSale, movements,
    previousSessionRejected: true,
  }, null, 2));
  console.log(`PASS: stock=8, venta=${sale.id}, total=3000, cambio=2000, movimiento=-2.`);
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  for (const entry of [...children].reverse()) {
    try { await stop(entry); } catch (error) { console.error(error); process.exitCode = 1; }
  }
  if (interrupted) process.exitCode = 1;
  if (directory) console.log(`Procesos propios finalizados. Datos conservados: ${directory}`);
}
