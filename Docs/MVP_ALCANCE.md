# Alcance real

Fecha: 2026-11-18. Estado tecnico: flujo local implementado y probado. Aceptacion del PO: pendiente, no simulada. Ver trazabilidad en MVP_ANALISIS.md.

| HU | Estado del corte | Alcance / limite |
|---|---|---|
| 001 | Implementado | Alta de productos, categorias por administrador, unicidad y filtro; no edicion/borrado porque no son criterios de esta HU |
| 002 | Implementado | Busqueda por nombre/codigo, filtros, existencias reales y vacios |
| 007 | Implementado | Carrito editable previo al cobro y validacion servidor |
| 010 | Implementado | Pago/venta/movimientos/descuento atomicos; cancelar antes de cobrar no escribe |
| 020 | Implementado | Login individual BCrypt, sesion 30 minutos, logout; cuenta activa comprobada en servidor |
| 022 | Implementado en modulos del corte | Matriz ADMIN/CASHIER/WAREHOUSE aplicada en servidor; no existe panel financiero ni gestion de permisos todavia |
| 008 | Parcial, P0 reducido cumplido | Efectivo recibido y cambio; tarjeta/Nequi/PSE/verificacion digital pendientes |
| 009 | Implementado | Recibo de venta pagada, historial, descarga TXT e impresion/PDF del navegador; no factura electronica |
| 003-006 | Pendiente P1 | Entradas, ajustes, minimos/alertas, perdidas e historial consultable; movimientos de venta si se guardan |
| 011-014 | Pendiente P1 | Pedidos, devoluciones, anulaciones y cierres |
| 015-018 | Pendiente P1 | Proveedores, compras, recepciones y precios |
| 021,023-025 | Pendiente P1 | Administracion de empleados, desactivacion, turnos y actividad; tres cuentas seed no son CRUD de empleados |
| 026-030 | Pendiente P1 | Gastos y reportes financieros/ganancias/perdidas; no cifras artificiales |
| 019,031,032 | Pendiente P2 | Sugerencias, comparaciones y exportacion de informes |

## Implementacion transversal

Persistencia MongoDB durable, migracion V001 de indices/validadores y comprobacion de documentos incompatibles, seed idempotente opt-in, variables de entorno, roles, errores JSON y validacion estricta. UI responsive en espanol, formularios y dialogos accesibles, carga, vacios, confirmacion de cobro y notificaciones. CSS propio para conservar el mockup sin una dependencia Tailwind innecesaria. No se modificaron documentos originales.

## Limites conocidos

Una empresa y una instancia backend local. Sesiones en memoria: reiniciar requiere identificarse de nuevo, no pierde operaciones. Listados completos sin paginacion. No idempotency key de cobro: ante respuesta perdida consultar historial antes de repetir; doble click simultaneo en UI bloqueado, pero reintentos manuales pueden registrar otra venta. Mongo/seed no coordinan arranques simultaneos de varias instancias. Respaldo manual documentado, no programado ni medido bajo RTO. Cuentas y Mongo sin autenticacion solo para loopback; no apto para exposicion publica. Sin limitador de intentos de login. No se midieron carga, disponibilidad, Firefox/Edge ni prueba humana 4/5.

Los documentos originales mantienen sus propuestas y estados de aprobacion. Clasificar una HU aqui como implementada describe evidencia tecnica del corte, no firma academica ni compromiso de entregar todas las Must.
