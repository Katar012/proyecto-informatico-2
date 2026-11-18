# Arquitectura del MVP

## Decisiones

Se conservan React/TypeScript/Vite y Java 21/Spring Boot/Spring Data MongoDB de los diagramas C4 de D05. Monolito modular: navegador → REST JSON → servicio transaccional → MongoDB local replica set rs0. Docker Compose prepara Mongo con volumen persistente; backend Maven y frontend npm se pueden ejecutar fuera de Docker. Sin servicios externos ni pagos reales.

## Modelo

Colecciones users (id, name, email unico, passwordHash BCrypt, role ADMIN/CASHIER/WAREHOUSE, active), categories (id, name unico normalizado), products (id, code unico, name, categoryId, price entero COP, stock entero >=0), sales (id, cashierId/name, createdAt, items embebidos con productId/code/name/quantity/unitPrice/subtotal, total, tendered, change, paymentMethod CASH), movements (id, productId, saleId, quantity negativa, authorId, createdAt). Sesiones opacas en servidor con cookie HttpOnly y vencimiento de inactividad de 30 min. No se borran usuarios ni ventas.

Migracion versionada V001 crea indices unicos y validadores/restricciones adecuados; seed idempotente opt-in para tres usuarios y catalogo ficticio. Cobro: validar item unico y cantidad positiva, leer precios del servidor, actualizar stock condicionalmente por disponibilidad, guardar venta y movimientos dentro de transaccion Mongo. Rollback ante cualquier fallo. Precio y cantidad acotados para evitar overflow. No confiar en totales enviados por navegador.

## Contrato REST del corte

Prefijo `/api`. Errores JSON `{message}`; 400 validacion, 401 sin sesion, 403 cargo prohibido, 404 inexistente, 409 duplicado/stock insuficiente. Cookie de sesion same-origin. GET no modifica informacion. POST requiere origen local permitido para prevenir CSRF; proxy Vite en desarrollo.

| Metodo y ruta | Entrada / salida | Permiso |
|---|---|---|
| POST /auth/login | email,password → usuario publico | publico |
| GET /auth/me | usuario publico | autenticado |
| POST /auth/logout | termina sesion | autenticado |
| GET /categories | categorias | todos |
| POST /categories | name → categoria | ADMIN |
| GET /products | q y categoryId opcionales → productos | todos |
| POST /products | code,name,categoryId,price,stock → producto | ADMIN,WAREHOUSE |
| GET /sales | historial con detalle | ADMIN,CASHIER |
| POST /sales | items[{productId,quantity}],tendered → venta pagada | ADMIN,CASHIER |

El contrato se establece aqui antes de implementar. No se publica workspace remoto ni se agrega infraestructura Postman obligatoria a un MVP local sin consumidores externos.

## Estructura y navegacion

`backend/`: pom, configuracion, modelos, repositorios, API, servicio y pruebas. `frontend/`: React, cliente API, vistas y estilos. `compose.yaml`: Mongo replica set con volumen. `scripts/`: verificacion e inicializacion si necesaria. `Docs/`: evidencia, alcance y demo. Login → inventario (todos); alta productos (admin/bodega); categorias (admin); caja/historial/comprobante (admin/cajero). Sin enlaces a modulos pendientes. Estados de carga, vacio y errores y moneda COP consistente.

## Seguridad y operacion

BCrypt; cookie HttpOnly SameSite Strict; sesion expira tras 30 min; solo datos publicos de usuario. Variables de entorno para URI/puerto/seed/origen. Credenciales seed locales documentadas, nunca para produccion. Mongo expuesto solo a loopback. Volumen persiste al reiniciar; no usar `docker compose down -v` salvo borrado deliberado. Copia via mongodump y restauracion via mongorestore con aplicacion detenida, ver README. HTTPS obligatorio fuera de loopback, despliegue publico fuera de alcance.

## Verificacion prevista

Pruebas backend reales contra Mongo replica set: login/logout/permisos, duplicados, campos invalidos, filtros, stock, transacciones y reinicio. Pruebas de UI si navegador disponible. Ejecutar compilacion Java, tipos/lint/build frontend. Registrar resultados reales y carencias en MVP_PRUEBAS sin suplantar aprobacion PO.
