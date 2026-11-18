import React, { useEffect, useRef, useState } from "react";
import {
  Boxes,
  ShoppingBag,
  ShoppingCart,
  ReceiptText,
  LogOut,
  Search,
  Plus,
  X,
  ArrowRight,
  Check,
  Download,
  Printer,
  Package,
  RefreshCw,
} from "lucide-react";
import {
  api,
  ApiError,
  money,
  date,
  type User,
  type Category,
  type Product,
  type Sale,
} from "./api";
import "./styles.css";

type View = "inventory" | "checkout" | "history";
const roles = {
  ADMIN: "Administrador",
  CASHIER: "Cajero",
  WAREHOUSE: "Bodeguero",
};
const message = (e: unknown) =>
  e instanceof Error ? e.message : "Ocurrió un error inesperado.";
function Brand() {
  return (
    <div className="brand">
      <span className="brand-icon">
        <ShoppingBag size={23} />
      </span>
      <span>
        Smart<span className="violet">Biz</span>
        <small>GESTIÓN SIMPLE, CADA DÍA</small>
      </span>
    </div>
  );
}
export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [boot, setBoot] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const [view, setView] = useState<View>("inventory");
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [sales, setSales] = useState<Sale[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("");
  const [revision, setRevision] = useState(0);
  const [modal, setModal] = useState<"product" | "category" | "confirm" | null>(
    null,
  );
  const [cart, setCart] = useState<{ product: Product; quantity: number }[]>(
    [],
  );
  const [tendered, setTendered] = useState("");
  const [receipt, setReceipt] = useState<Sale | null>(null);
  const submitting = useRef(false);
  function fail(e: unknown) {
    if (e instanceof ApiError && e.status === 401) {
      setUser(null);
      setCart([]);
      setModal(null);
      setReceipt(null);
    }
    setError(message(e));
  }
  useEffect(() => {
    api<User>("/auth/me")
      .then(setUser)
      .catch((e) => {
        if (!(e instanceof ApiError && e.status === 401)) setError(message(e));
      })
      .finally(() => setBoot(false));
  }, []);
  useEffect(() => {
    if (!user) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setLoading(true);
      const params = new URLSearchParams();
      if (query) params.set("q", query);
      if (category) params.set("categoryId", category);
      Promise.all([
        api<Category[]>("/categories", undefined, controller.signal),
        view === "history"
          ? api<Sale[]>("/sales", undefined, controller.signal)
          : api<Product[]>(`/products?${params}`, undefined, controller.signal),
      ])
        .then(([cats, data]) => {
          setCategories(cats);
          if (view === "history") setSales(data as Sale[]);
          else setProducts(data as Product[]);
        })
        .catch((e) => {
          if (!controller.signal.aborted) fail(e);
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, 200);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [user, view, query, category, revision]);
  async function perform(action: () => Promise<void>) {
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      await action();
    } catch (e) {
      fail(e);
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }
  function navigate(next: View) {
    setView(next);
    setQuery("");
    setCategory("");
    setError("");
    setSuccess("");
    setProducts([]);
    setSales([]);
  }
  const total = cart.reduce(
    (sum, item) => sum + item.product.price * item.quantity,
    0,
  );
  const cash = Number(tendered);
  const validCash = Number.isSafeInteger(cash) && cash >= total && total > 0;
  function add(product: Product) {
    setCart((current) => {
      const existing = current.find((i) => i.product.id === product.id);
      if (existing && existing.quantity >= product.stock) return current;
      return existing
        ? current.map((i) =>
            i.product.id === product.id
              ? { ...i, quantity: i.quantity + 1 }
              : i,
          )
        : [...current, { product, quantity: 1 }];
    });
  }
  const notices = (
    <>
      {error && (
        <div role="alert" className="notice error">
          {error}
          <button aria-label="Cerrar error" onClick={() => setError("")}>
            <X size={16} />
          </button>
        </div>
      )}
      {success && (
        <div role="status" className="notice success">
          <Check size={18} />
          {success}
        </div>
      )}
    </>
  );
  if (boot)
    return (
      <main className="boot" role="status">
        <Brand />
        <p>Preparando tu espacio de trabajo…</p>
      </main>
    );
  if (!user)
    return (
      <main className="login">
        <section className="login-story">
          <Brand />
          <div>
            <span className="eyebrow">TU NEGOCIO, EN ORDEN</span>
            <h1>
              Menos vueltas.
              <br />
              Más control.
            </h1>
            <p>
              Tu inventario y tu caja, conectados en un solo lugar. Concéntrate
              en lo que mejor haces.
            </p>
            <div className="story-card">
              <Boxes size={30} />
              <div>
                <strong>Cada producto cuenta.</strong>
                <p>
                  Consulta existencias, vende y conserva el detalle de cada
                  operación.
                </p>
              </div>
            </div>
          </div>
          <small>SmartBiz · Operación local</small>
        </section>
        <section className="login-form">
          <div className="login-box">
            <span className="eyebrow">BIENVENIDO A SMARTBIZ</span>
            <h2>Inicia sesión</h2>
            <p className="muted">Ingresa con tu cuenta para continuar.</p>
            {notices}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const form = new FormData(e.currentTarget);
                void perform(async () => {
                  const account = await api<User>("/auth/login", {
                    email: form.get("email"),
                    password: form.get("password"),
                  });
                  setUser(account);
                  setView("inventory");
                  setCart([]);
                  setTendered("");
                });
              }}
            >
              <label>
                Correo electrónico
                <input
                  name="email"
                  type="email"
                  autoComplete="username"
                  placeholder="tu@smartbiz.local"
                  required
                />
              </label>
              <label>
                Contraseña
                <input
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  required
                />
              </label>
              <button className="primary wide" disabled={busy}>
                {busy ? "Ingresando…" : "Iniciar sesión"}
                <ArrowRight size={18} />
              </button>
            </form>
            <details className="demo">
              <summary>Cuentas de demostración local</summary>
              <p>
                Administrador: admin@smartbiz.local
                <br />
                Caja: caja@smartbiz.local
                <br />
                Bodega: bodega@smartbiz.local
              </p>
              <p>
                Contraseña: <code>DemoSmartBiz2026!</code>
              </p>
              <small>Datos ficticios. Solo para la demostración local.</small>
            </details>
          </div>
        </section>
      </main>
    );
  const canSell = user.role !== "WAREHOUSE";
  return (
    <div className="app">
      <a className="skip" href="#content">
        Saltar al contenido
      </a>
      <aside className="sidebar">
        <Brand />
        <p className="nav-label">ESPACIO DE TRABAJO</p>
        <nav aria-label="Principal">
          <button
            className={view === "inventory" ? "active" : ""}
            onClick={() => navigate("inventory")}
          >
            <Boxes size={20} />
            Inventario
          </button>
          {canSell && (
            <>
              <button
                className={view === "checkout" ? "active" : ""}
                onClick={() => navigate("checkout")}
              >
                <ShoppingCart size={20} />
                Caja
                {cart.length > 0 && (
                  <span className="count">{cart.length}</span>
                )}
              </button>
              <button
                className={view === "history" ? "active" : ""}
                onClick={() => navigate("history")}
              >
                <ReceiptText size={20} />
                Historial de ventas
              </button>
            </>
          )}
        </nav>
        <div className="sidebar-note">
          <span className="dot" /> Operación local
          <p>
            Una buena gestión empieza
            <br />
            con información al día.
          </p>
        </div>
        <div className="profile">
          <span className="avatar">{user.name.slice(0, 1)}</span>
          <div>
            <strong>{user.name}</strong>
            <small>{roles[user.role]}</small>
          </div>
          <button
            title="Cerrar sesión"
            aria-label="Cerrar sesión"
            disabled={busy}
            onClick={() =>
              void perform(async () => {
                await api("/auth/logout", {});
                setUser(null);
                setCart([]);
                setReceipt(null);
                setModal(null);
              })
            }
          >
            <LogOut size={18} />
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            Mi negocio{" "}
            <span className="muted">
              /{" "}
              {view === "inventory"
                ? "Inventario"
                : view === "checkout"
                  ? "Caja"
                  : "Historial"}
            </span>
          </span>
          <span className="role-badge">{roles[user.role]}</span>
        </header>
        <main id="content">
          <div className="page-heading">
            <div>
              <span className="eyebrow">
                {view === "inventory"
                  ? "TODO EN SU LUGAR"
                  : view === "checkout"
                    ? "PUNTO DE VENTA"
                    : "TRAZABILIDAD DE TU NEGOCIO"}
              </span>
              <h1>
                {view === "inventory"
                  ? "Inventario"
                  : view === "checkout"
                    ? "Nueva venta"
                    : "Historial de ventas"}
              </h1>
              <p className="muted">
                {view === "inventory"
                  ? "Consulta y organiza los productos de tu negocio."
                  : view === "checkout"
                    ? "Agrega productos, revisa el pedido y cobra en efectivo."
                    : "Consulta las ventas pagadas y recupera sus comprobantes."}
              </p>
            </div>
            <div className="actions">
              {view === "inventory" && user.role === "ADMIN" && (
                <button
                  className="secondary"
                  onClick={() => setModal("category")}
                >
                  <Plus size={17} />
                  Categoría
                </button>
              )}
              {view === "inventory" && user.role !== "CASHIER" && (
                <button className="primary" onClick={() => setModal("product")}>
                  <Plus size={18} />
                  Nuevo producto
                </button>
              )}
              <button
                className="icon-button"
                aria-label="Actualizar datos"
                onClick={() => setRevision((r) => r + 1)}
              >
                <RefreshCw size={18} />
              </button>
            </div>
          </div>
          {notices}
          {view !== "history" ? (
            <div className={view === "checkout" ? "pos-layout" : ""}>
              <section className="panel catalog">
                <div className="panel-heading">
                  <div>
                    <h2>
                      {view === "checkout"
                        ? "Catálogo de productos"
                        : "Tus productos"}
                    </h2>
                    <p className="muted">
                      {loading
                        ? "Actualizando…"
                        : `${products.length} productos en esta consulta`}
                    </p>
                  </div>
                  <span className="soft-icon">
                    <Package size={22} />
                  </span>
                </div>
                <div className="filters">
                  <label className="search">
                    <Search size={18} />
                    <input
                      aria-label="Buscar por nombre o código"
                      placeholder="Buscar por nombre o código…"
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                    />
                  </label>
                  <select
                    aria-label="Filtrar por categoría"
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                  >
                    <option value="">Todas las categorías</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                {loading ? (
                  <div className="empty" role="status">
                    Cargando productos…
                  </div>
                ) : products.length === 0 ? (
                  <div className="empty">
                    <Package size={34} />
                    <h3>No hay productos para mostrar</h3>
                    <p>Prueba otra búsqueda o registra tu primer producto.</p>
                  </div>
                ) : (
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Producto</th>
                          <th>Categoría</th>
                          <th>Existencias</th>
                          <th className="numeric">Precio</th>
                          {view === "checkout" && (
                            <th>
                              <span className="sr-only">Agregar</span>
                            </th>
                          )}
                        </tr>
                      </thead>
                      <tbody>
                        {products.map((p) => (
                          <tr key={p.id}>
                            <td>
                              <strong>{p.name}</strong>
                              <small>{p.code}</small>
                            </td>
                            <td>
                              {categories.find((c) => c.id === p.categoryId)
                                ?.name || "Sin información"}
                            </td>
                            <td>
                              <span
                                className={`stock ${p.stock === 0 ? "out" : ""}`}
                              >
                                {p.stock === 0
                                  ? "Agotado"
                                  : `${p.stock} unidades`}
                              </span>
                            </td>
                            <td className="numeric price">{money(p.price)}</td>
                            {view === "checkout" && (
                              <td>
                                <button
                                  className="add-button"
                                  aria-label={`Agregar ${p.name}`}
                                  disabled={
                                    p.stock === 0 ||
                                    (cart.find((i) => i.product.id === p.id)
                                      ?.quantity || 0) >= p.stock
                                  }
                                  onClick={() => add(p)}
                                >
                                  <Plus size={18} />
                                </button>
                              </td>
                            )}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                <div className="panel-footer">
                  Precios en pesos colombianos (COP) · Existencias confirmadas
                  al cobrar
                </div>
              </section>
              {view === "checkout" && (
                <aside className="panel cart">
                  <div className="panel-heading">
                    <h2>
                      <ShoppingCart size={20} />
                      Venta actual
                    </h2>
                    <span className="count">{cart.length}</span>
                  </div>
                  {cart.length === 0 ? (
                    <div className="empty">
                      <ShoppingBag size={34} />
                      <h3>Tu venta empieza aquí</h3>
                      <p>Agrega productos del catálogo.</p>
                    </div>
                  ) : (
                    <div className="cart-items">
                      {cart.map(({ product: p, quantity }) => (
                        <div className="cart-item" key={p.id}>
                          <div>
                            <strong>{p.name}</strong>
                            <small>{money(p.price)} por unidad</small>
                            <label className="quantity">
                              Cantidad
                              <input
                                aria-label={`Cantidad de ${p.name}`}
                                type="number"
                                min="1"
                                max={p.stock}
                                step="1"
                                value={quantity}
                                onChange={(e) => {
                                  const n = Number(e.target.value);
                                  setCart((items) =>
                                    items.map((i) =>
                                      i.product.id === p.id
                                        ? { ...i, quantity: n }
                                        : i,
                                    ),
                                  );
                                }}
                              />
                            </label>
                          </div>
                          <div>
                            <button
                              className="remove"
                              aria-label={`Quitar ${p.name}`}
                              onClick={() =>
                                setCart((items) =>
                                  items.filter((i) => i.product.id !== p.id),
                                )
                              }
                            >
                              <X size={16} />
                            </button>
                            <strong>{money(p.price * quantity)}</strong>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                  <div className="cart-bottom">
                    <div className="total">
                      <span>Total</span>
                      <strong>{money(total)}</strong>
                    </div>
                    <span className="cash-tag">Pago en efectivo</span>
                    <label>
                      Efectivo recibido (COP)
                      <input
                        inputMode="numeric"
                        type="number"
                        min={total || 1}
                        step="1"
                        placeholder="0"
                        value={tendered}
                        onChange={(e) => setTendered(e.target.value)}
                      />
                    </label>
                    <div className="change">
                      <span>Cambio</span>
                      <strong>{money(validCash ? cash - total : 0)}</strong>
                    </div>
                    {tendered && !validCash && (
                      <p className="field-error">
                        Ingresa un monto entero que cubra el total.
                      </p>
                    )}
                    <button
                      className="primary wide"
                      disabled={
                        busy ||
                        !validCash ||
                        cart.some(
                          (i) =>
                            !Number.isSafeInteger(i.quantity) ||
                            i.quantity < 1 ||
                            i.quantity > i.product.stock,
                        )
                      }
                      onClick={() => setModal("confirm")}
                    >
                      Revisar y cobrar
                      <ArrowRight size={17} />
                    </button>
                    <p className="fine-print">
                      El inventario se descuenta al confirmar el pago.
                    </p>
                  </div>
                </aside>
              )}
            </div>
          ) : (
            <section className="panel">
              <div className="panel-heading">
                <h2>Ventas pagadas</h2>
                <span className="muted">{sales.length} registros</span>
              </div>
              {loading ? (
                <div className="empty" role="status">
                  Cargando ventas…
                </div>
              ) : sales.length === 0 ? (
                <div className="empty">
                  <ReceiptText size={34} />
                  <h3>Aún no hay ventas</h3>
                  <p>Las ventas confirmadas aparecerán aquí.</p>
                </div>
              ) : (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Comprobante</th>
                        <th>Fecha</th>
                        <th>Cajero</th>
                        <th className="numeric">Total</th>
                        <th>Detalle</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sales.map((s) => (
                        <tr key={s.id}>
                          <td>
                            <strong className="sale-id">{s.id}</strong>
                            <small>Efectivo · Pagada</small>
                          </td>
                          <td>{date(s.createdAt)}</td>
                          <td>{s.cashierName}</td>
                          <td className="numeric price">{money(s.total)}</td>
                          <td>
                            <button
                              className="text-button"
                              onClick={() => setReceipt(s)}
                            >
                              Ver recibo
                              <ArrowRight size={15} />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          )}
          <footer className="page-footer">
            SmartBiz <span>Hecho para el día a día de tu negocio.</span>
          </footer>
        </main>
      </div>
      {modal && (
        <Modal
          title={
            modal === "product"
              ? "Nuevo producto"
              : modal === "category"
                ? "Nueva categoría"
                : "Confirmar cobro"
          }
          close={() => {
            if (!busy) setModal(null);
          }}
          busy={busy}
        >
          {modal === "confirm" ? (
            <>
              <p>
                Vas a registrar una venta pagada en efectivo. El servidor
                verificará los precios y las existencias.
              </p>
              <dl className="confirmation">
                <div>
                  <dt>Productos</dt>
                  <dd>{cart.reduce((s, i) => s + i.quantity, 0)} unidades</dd>
                </div>
                <div>
                  <dt>Total estimado</dt>
                  <dd>{money(total)}</dd>
                </div>
                <div>
                  <dt>Efectivo recibido</dt>
                  <dd>{money(cash)}</dd>
                </div>
                <div>
                  <dt>Cambio estimado</dt>
                  <dd>{money(cash - total)}</dd>
                </div>
              </dl>
              {error && (
                <p role="alert" className="field-error">
                  {error}
                </p>
              )}
              <button
                className="primary wide"
                disabled={busy}
                onClick={() =>
                  void perform(async () => {
                    const sale = await api<Sale>("/sales", {
                      items: cart.map((i) => ({
                        productId: i.product.id,
                        quantity: i.quantity,
                      })),
                      tendered: cash,
                    });
                    setCart([]);
                    setTendered("");
                    setModal(null);
                    setReceipt(sale);
                    setRevision((r) => r + 1);
                    setSuccess(
                      "Venta registrada. El inventario se ha actualizado.",
                    );
                  })
                }
              >
                {busy ? "Registrando pago…" : "Confirmar pago en efectivo"}
              </button>
              <p className="fine-print">
                Si hay un error de conexión, consulta el historial antes de
                volver a cobrar para evitar duplicados.
              </p>
            </>
          ) : (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const data = new FormData(e.currentTarget);
                void perform(async () => {
                  if (modal === "category")
                    await api("/categories", {
                      name: String(data.get("name")).trim(),
                    });
                  else
                    await api("/products", {
                      name: String(data.get("name")).trim(),
                      code: String(data.get("code")).trim(),
                      categoryId: data.get("categoryId"),
                      price: Number(data.get("price")),
                      stock: Number(data.get("stock")),
                    });
                  setSuccess(
                    modal === "category"
                      ? "Categoría creada correctamente."
                      : "Producto creado correctamente.",
                  );
                  setModal(null);
                  setRevision((r) => r + 1);
                });
              }}
            >
              <label>
                Nombre
                <input
                  name="name"
                  required
                  maxLength={modal === "category" ? 100 : 160}
                  pattern=".*\S.*"
                  autoFocus
                />
              </label>
              {modal === "product" && (
                <>
                  <label>
                    Código
                    <input
                      name="code"
                      required
                      maxLength={40}
                      pattern="[A-Za-z0-9._\-]+"
                    />
                  </label>
                  <label>
                    Categoría
                    <select name="categoryId" required defaultValue="">
                      <option value="" disabled>
                        Selecciona una categoría
                      </option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  {categories.length === 0 && (
                    <p className="field-error">
                      Un administrador debe crear una categoría antes de
                      registrar productos.
                    </p>
                  )}
                  <div className="form-row">
                    <label>
                      Precio (COP)
                      <input
                        name="price"
                        type="number"
                        min="1"
                        max="1000000000"
                        step="1"
                        required
                      />
                    </label>
                    <label>
                      Existencias iniciales
                      <input
                        name="stock"
                        type="number"
                        min="0"
                        max="1000000"
                        step="1"
                        required
                      />
                    </label>
                  </div>
                </>
              )}
              {error && (
                <p role="alert" className="field-error">
                  {error}
                </p>
              )}
              <button
                className="primary wide"
                disabled={busy || (modal === "product" && !categories.length)}
              >
                {busy ? "Guardando…" : "Guardar"}
              </button>
            </form>
          )}
        </Modal>
      )}
      {receipt && (
        <Modal title="Comprobante de venta" close={() => setReceipt(null)}>
          <Receipt sale={receipt} />
          <div className="receipt-actions">
            <button className="secondary" onClick={() => window.print()}>
              <Printer size={17} />
              Imprimir
            </button>
            <button
              className="primary"
              onClick={() => {
                const s = receipt;
                const text = [
                  "SMARTBIZ · COMPROBANTE DE VENTA",
                  `Venta: ${s.id}`,
                  date(s.createdAt),
                  `Cajero: ${s.cashierName}`,
                  "",
                  ...s.items.map(
                    (i) =>
                      `${i.code} | ${i.name}\n${i.quantity} x ${money(i.unitPrice)} = ${money(i.subtotal)}`,
                  ),
                  "",
                  `Total: ${money(s.total)}`,
                  `Efectivo: ${money(s.tendered)}`,
                  `Cambio: ${money(s.change)}`,
                  "Medio de pago: Efectivo",
                ].join("\n");
                const url = URL.createObjectURL(
                  new Blob([text], { type: "text/plain;charset=utf-8" }),
                );
                const a = document.createElement("a");
                a.href = url;
                a.download = `smartbiz-${s.id.replace(/[^a-zA-Z0-9_-]/g, "")}.txt`;
                a.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
              }}
            >
              <Download size={17} />
              Descargar TXT
            </button>
          </div>
          <p className="fine-print">
            Para guardar en PDF, elige «Guardar como PDF» al imprimir.
          </p>
        </Modal>
      )}
    </div>
  );
}
function Modal({
  title,
  close,
  busy = false,
  children,
}: {
  title: string;
  close: () => void;
  busy?: boolean;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement as HTMLElement | null;
    dialog?.showModal();
    return () => {
      dialog?.close();
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby="modal-title"
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) close();
      }}
    >
      <div className="modal-heading">
        <h2 id="modal-title">{title}</h2>
        <button aria-label="Cerrar ventana" disabled={busy} onClick={close}>
          <X size={21} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
function Receipt({ sale: s }: { sale: Sale }) {
  return (
    <article className="receipt">
      <Brand />
      <div className="paid">
        <Check size={23} />
      </div>
      <h2>¡Venta realizada!</h2>
      <p className="muted">Comprobante de venta · Efectivo</p>
      <dl>
        <div>
          <dt>Número</dt>
          <dd className="sale-id">{s.id}</dd>
        </div>
        <div>
          <dt>Fecha</dt>
          <dd>{date(s.createdAt)}</dd>
        </div>
        <div>
          <dt>Cajero</dt>
          <dd>{s.cashierName}</dd>
        </div>
      </dl>
      <div className="receipt-lines">
        {s.items.map((i) => (
          <div key={i.productId}>
            <div>
              <strong>{i.name}</strong>
              <small>
                {i.code} · {i.quantity} × {money(i.unitPrice)}
              </small>
            </div>
            <strong>{money(i.subtotal)}</strong>
          </div>
        ))}
      </div>
      <dl>
        <div className="total">
          <dt>Total pagado</dt>
          <dd>{money(s.total)}</dd>
        </div>
        <div>
          <dt>Efectivo recibido</dt>
          <dd>{money(s.tendered)}</dd>
        </div>
        <div>
          <dt>Cambio</dt>
          <dd>{money(s.change)}</dd>
        </div>
      </dl>
      <p className="fine-print">
        Gracias por tu compra.
        <br />
        Comprobante de operación local, no factura electrónica.
      </p>
    </article>
  );
}
