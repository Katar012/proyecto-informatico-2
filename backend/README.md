# SmartBiz backend

Java 21, Maven, Spring Boot y MongoDB real. Este directorio es independiente del frontend y de la infraestructura.

## Arranque

Mongo debe estar iniciado como replica set (por defecto `rs0`), con PRIMARY disponible. La URI debe seleccionar una base de datos. El usuario Mongo necesita permisos para crear colecciones, indices y ejecutar `collMod` en esa base. No hay fallback en memoria ni compatibilidad con Mongo standalone para ventas.

Desde `backend/`:

```bash
MONGODB_URI='mongodb://localhost:27017/smartbiz?replicaSet=rs0' SEED_DEMO=true mvn spring-boot:run
```

O compilar con `mvn clean verify` y ejecutar `java -jar target/smartbiz-backend-0.0.1-SNAPSHOT.jar` con las mismas variables.

| Variable | Predeterminado | Uso |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/smartbiz?replicaSet=rs0` | Base durable, replica set |
| `PORT` | `8080` | Puerto HTTP |
| `SEED_DEMO` | `false` | Alta idempotente de datos ficticios |
| `ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080,http://127.0.0.1:8080` | Lista exacta separada por comas, sin barra final |
| `COOKIE_SECURE` | `false` | Activar con HTTPS |
| `TEST_MONGODB_URI` | sin valor | Habilita pruebas reales en una base exclusiva de pruebas |

Seed: `admin@smartbiz.local` (ADMIN), `caja@smartbiz.local` (CASHIER), `bodega@smartbiz.local` (WAREHOUSE). Password comun: `DemoSmartBiz2026!`. Crea categoria Demostracion y tres productos DEMO. Usa `$setOnInsert`: no repone stock consumido, no cambia passwords existentes ni elimina ventas al reiniciar. Solo para demo local.

V001 se ejecuta antes del seed: aplica validadores Mongo estrictos, indices unicos de email/nombre normalizado/codigo y de movimiento por venta/producto, indices de consulta y registra `schema_migrations/V001`. Reaplicar es idempotente y repara indices ausentes. Datos previos incompatibles o duplicados deben corregirse deliberadamente; la migracion no los borra ni los reescribe.

Como `collMod` no valida datos retroactivamente, V001 consulta cada coleccion con la negacion del mismo `$jsonSchema` antes de registrar la migracion. Si encuentra incompatibilidades, detiene el arranque con nombre de coleccion y hasta cinco IDs de ejemplo, sin exponer documentos completos ni modificar sus datos. Los validadores ya aplicados permanecen activos; no hay rollback de metadatos ni reparacion automatica. Ejecutar el arranque/migracion sin escritores externos concurrentes; no implementa coordinacion multiinstancia.

## Contrato frontend

Prefijo `/api`; usar proxy same-origin de Vite hacia `http://localhost:8080`. No habilita CORS: el navegador habla con su propio origen mediante el proxy. Conservar cookies (`credentials: 'same-origin'`, o equivalente). Los POST, incluido login/logout, exigen cabecera `Origin` de la lista; curl y clientes de pruebas deben enviarla explicitamente. No usar wildcard ni suprimir Origin en el proxy.

- `POST /auth/login`: `{email,password}` devuelve directamente `{id,name,email,role}`.
- `GET /auth/me`: mismo usuario publico. `POST /auth/logout`: 204, invalida sesion y elimina cookie.
- `GET /categories`: array de `{id,name}`. `POST /categories`: `{name}`, ADMIN.
- `GET /products?q=&categoryId=`: array de `{id,code,name,categoryId,price,stock}`. Busqueda literal parcial por nombre/codigo, sin interpretar regex. `POST /products`: esos campos sin id; ADMIN/WAREHOUSE.
- `GET /sales`: array de ventas completas, mas recientes primero; ADMIN/CASHIER.
- `POST /sales`: `{items:[{productId,quantity}],tendered}`, ADMIN/CASHIER. Devuelve `{id,cashierId,cashierName,createdAt,items:[{productId,code,name,quantity,unitPrice,subtotal}],total,tendered,change,paymentMethod}`; `paymentMethod` siempre `CASH`, fecha ISO-8601 UTC.
- Altas y cobros devuelven 201. Errores `{message}`: 400 datos invalidos/pago insuficiente, 401 sin sesion, 403 cargo/origen, 404 referencia inexistente, 409 duplicado/stock insuficiente. 500 fallo inesperado, 503 commit de resultado incierto: consultar historial antes de volver a cobrar.

Todos los listados requieren autenticacion. No se publican hashes ni nombres normalizados. Categorias unicas ignorando mayusculas y espacios repetidos; codigos ASCII de 1-40 caracteres `[A-Za-z0-9._-]`, canonizados a mayusculas. Nombres categoria/producto maximo 100/160 caracteres; ids hexadecimales de 24 caracteres. Precio 1..1.000.000.000 COP, stock 0..1.000.000, cantidad 1..1.000.000, maximo 100 items distintos, dinero hasta 1.000.000.000.000.000 COP. Numeros JSON enteros, no strings, decimales ni totales provistos por cliente. Campos desconocidos se rechazan.

El filtro solo admite las rutas canonicas exactas del contrato, inspeccionando la URI cruda antes de MVC. Rechaza con JSON 404 rutas con parametros `;`, codificacion `%`, segmentos alternativos, barras duplicadas/finales y rutas desconocidas, incluso fuera del prefijo literal `/api/`. Los permisos son una lista explicita de metodo/ruta/rol, con denegacion por defecto (403 para metodos no autorizados de un usuario autenticado). No admite HEAD/OPTIONS implicitamente.

Sesiones opacas del contenedor, solo cookie `JSESSIONID`, HttpOnly/SameSite=Strict, 30 minutos de inactividad. Login reemplaza sesion anterior; logout la revoca. Las sesiones estan en memoria del proceso y reiniciar exige login de nuevo; Mongo conserva catalogo/ventas/movimientos. Respuestas API `Cache-Control: no-store`. HTTP solo para operacion local; HTTPS requerido fuera de loopback. No incluye rate limiting distribuido ni gestion de usuarios, fuera de este corte.

Cobro usa `MongoTransactionManager`: precios leidos del servidor, snapshots inmutables, decrementos condicionales de stock, venta y movimientos en una sola transaccion. Un fallo de cualquier item o pago insuficiente revierte todas las escrituras. Conflictos transitorios abortados se reintentan hasta cuatro veces, nunca se repite automaticamente un commit de resultado incierto. No hay endpoint de ajustes/movimientos adicional en el contrato MVP; movimientos se guardan en la coleccion `movements`.

## Pruebas

```bash
mvn clean verify
TEST_MONGODB_URI='mongodb://localhost:27017/smartbiz_test?replicaSet=rs0' mvn verify
```

Sin URI de pruebas, se ejecutan pruebas unitarias y la integracion se marca omitida. Con URI, arranca servidor real en puerto aleatorio y prueba HTTP real contra Mongo: BCrypt/login/logout/cookie, roles, CSRF, unicidad, filtros, limites/overflow, snapshots/cambio, rollback por pago insuficiente y fallo de item posterior, dos cobros concurrentes que compiten por stock y reaplicacion de migracion/seed. Incluye variantes HTTP semicolon/codificadas para los tres roles y denegacion de metodos no declarados. La base de pruebas configurada se conserva, con registros de nombre aleatorio; **usar una base exclusiva**, nunca produccion.

`MigrationIntegrationTest` crea bases aisladas de nombre aleatorio `smartbiz_v001_test_<uuid>` en el mismo servidor: inserta documentos incompatibles previos a V001 en cada una de las cinco colecciones, verifica diagnostico, conservacion de datos y ausencia de registro V001, y comprueba que una limpieza explicita del fixture permite finalizar. Elimina exclusivamente esas bases temporales en `finally`, nunca la base configurada en la URI. Requiere permisos de creacion y borrado de esas bases de pruebas.

Los reportes estan en `target/surefire-reports/`. La reaplicacion de seed/migracion verifica idempotencia, no sustituye una prueba de reinicio completo de Mongo ni una medicion de cobertura o carga.
