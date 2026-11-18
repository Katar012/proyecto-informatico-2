# Verificacion ejecutada

Fecha real de ejecucion: 2026-11-18. Entorno Linux, Java 21.0.13, Maven 3.8.7, Node 26.7.0, npm 11.19, MongoDB real 8.0.17 replica set. No resultados de mock presentados como integracion. Pruebas tecnicas no equivalen a aceptacion del PO.

## Comandos y resultados

| Comando desde raiz | Resultado real |
|---|---|
| `TEST_MONGODB_URI='mongodb://localhost:27017/smartbiz_test?replicaSet=rs0' mvn -B -ntp -f backend/pom.xml clean verify` | BUILD SUCCESS, 13 pruebas, 0 fallos/errores/omitidas |
| `npm --prefix frontend run lint` | Correcto, sin advertencias |
| `npm --prefix frontend run typecheck` | Correcto |
| `npm --prefix frontend run build` | Correcto, bundle Vite generado |
| `npx playwright install chromium` | Instalado Chromium; distribucion Linux usa fallback ubuntu24.04 |
| `npm run test:e2e` | 3 aprobadas, ultima ejecucion 11,5 s sobre JAR final |
| `TMPDIR=/tmp/opencode MONGOD_BINARY=/tmp/opencode/mongodb-linux-x86_64-ubuntu2404-8.0.17/bin/mongod npm run test:persistence` | PASS, backend y Mongo detenidos/reiniciados, stock 8, venta total 3000/cambio 2000, movimiento -2 |
| `docker compose config --quiet` | Configuracion valida |
| `docker info` | Acceso daemon denegado; no se ejecuto Mongo en Docker |
| `npm audit` (raiz) | 0 vulnerabilidades |
| `npm --prefix frontend audit --omit=dev` | 0 vulnerabilidades de produccion |
| `npm --prefix frontend audit --json` | 11 altas de desarrollo por cadena picomatch, salida 1 |
| `git diff --check` | Correcto |
| Postman request login local con asercion role CASHIER y ausencia de hash | HTTP200, 1 test aprobado, salida 0, sin reportar eventos remotos |

Mongo para verificacion se descargo de fastdl.mongodb.org y se ejecuto fuera del repo en `/tmp/opencode`, con dbpath real, sin base en memoria. El camino alternativo sin Docker de README reproduce el mismo mecanismo. Evidencia del ultimo test de reinicio: `/tmp/opencode/smartbiz-persistence-7qwMXY/result.json`, ids y logs, datos conservados. Es ruta de esta sesion, no dependencia del proyecto. Los reportes se regeneran; target y test-results no se versionan.

## Casos y trazabilidad P0

Ejecutor: agente de desarrollo mediante JUnit/HTTP/Playwright, no participantes humanos. Cada caso de esta tabla fue ejecutado en 2026-11-18 y esta aprobado. Precondiciones: Mongo replica PRIMARY, seed y servidor; E2E ademas navegador Chromium y Vite.

| Caso | HU / precondicion y pasos | Resultado esperado y observado | Evidencia |
|---|---|---|---|
| CP-HU-020-01/02/03 | Cuenta activa; login correcto/incorrecto, logout y reutilizar cookie | Entra solo correcto, cookie protegida, logout revoca | AuthTest, MongoIntegrationTest, E2E |
| CP-HU-022-03 | Cajero/bodeguero intentan rutas y POST ajenos, variantes ; y codificadas | 403 ruta canonica prohibida; variantes rechazadas sin ejecutar operacion | AccessTest y MongoIntegrationTest |
| CP-HU-001-01/02 | Crear categoria y producto valido, repetir codigo/categoria con variantes mayusculas | 201 valido, 409 duplicado, sin duplicacion | MongoIntegrationTest y E2E |
| CP-HU-001-03/05 | Campos invalidos, referencias inexistentes, precio decimal/overflow, cantidades invalidas | Rechazo sin guardado; limites de servidor | InputsTest, MongoIntegrationTest |
| CP-HU-001-07 / CP-HU-002-01/02 | Buscar codigo/nombre y filtrar categoria, buscar sin coincidencias | Solo coincidencias, estado vacio y sin modificar stock | MongoIntegrationTest y E2E |
| CP-HU-007-01/02 | Producto 1500 stock10; carrito cantidad3→2, efectivo5000 | Venta total3000/cambio2000, cantidad definitiva2 | E2E |
| CP-HU-007-03 | Dos cobros concurrentes de2 sobre stock3 | Uno 201, otro409, stock1, solo un movimiento | MongoIntegrationTest |
| CP-HU-008-01 | Efectivo insuficiente y suficiente | Insuficiente400 sin descuento; suficiente conserva efectivo/cambio | MongoIntegrationTest, E2E |
| CP-HU-009-01/02 | Venta pagada, consulta historial/recibo, descarga TXT | Detalle historico con precios/cajero/fecha, descarga disponible | MongoIntegrationTest y E2E |
| CP-HU-010-01/03 | Primer item valido y segundo inexistente; pago insuficiente tras decrementos | Rollback conserva stock y no agrega movimientos parciales | MongoIntegrationTest |
| CP-INTEGRIDAD | Intentar stock negativo directo en Mongo, cambiar precio tras venta | Validador rechaza negativo; venta conserva precio historico | MongoIntegrationTest |
| CP-MIGRACION | Reaplicar V001 y seed; introducir incompatibles en bases aisladas | Idempotencia; incompatibles detectados sin borrar datos ni marcar V001 | MongoIntegrationTest |
| CP-PERSISTENCIA | Producto10, vender2, detener Mongo y Java y reiniciar disco | Stock8, venta/items/cambio y movimiento conservados; sesion antigua invalida | scripts/test-persistence.mjs y result.json |
| CP-UI-MOVIL | Bodeguero viewport390x844, busqueda vacia | Opciones segun rol, sin overflow horizontal de documento, vacio visible | tests/smartbiz.spec.ts |

No se prueba borrado de ventas porque no existe en este corte y destruiria trazabilidad. La actualizacion critica probada es el descuento de stock; edicion de catalogo y administracion de cuentas no se presentan como CRUD implementado. El timeout30 se configura y prueba a nivel de sesion, no se espero manualmente 30 minutos en un navegador.

## Cobertura

JaCoCo 0.8.14, contadores LINE de `backend/target/site/jacoco/jacoco.xml`, medidos con integracion habilitada:

| Clase | Cubiertas / total | Porcentaje |
|---|---|---|
| Checkout | 34/35 | 97,14% |
| Access | 37/39 | 94,87% |
| Api | 36/37 | 97,30% |
| Inputs | 2/2 | 100% |
| V001 | 47/47 | 100% |
| Global | 203/213 | 95,31% |

Supera meta propuesta RNF-014 de 80% en los servicios medidos, sin afirmar cobertura de funcionalidades aplazadas. Cobertura de lineas no demuestra ausencia de errores ni sustituye carga/seguridad.

## Incidencias y limites

Primer E2E fallo por selector getByLabel de categoria (control presente en snapshot). Se cambio a rol combobox y se repitio: 3 aprobadas. Revision estatica detecto permisos dependientes de ruta literal; se corrigio politica deny-default y se agregaron regresiones HTTP con parametros y codificacion. V001 ahora detecta incompatibles previos, no solo impone validadores futuros.

Audit frontend reporta GHSA-fc5q-39xc-vjpf (ReDoS picomatch <=4.0.7) en dependencias dev. La ultima disponible consultada sigue afectada; downgrades sugeridos no eliminaron cadena e introdujeron otras alertas, se descartaron. No se aplicaron overrides inseguros ni se oculto audit. No exponga servidor Vite al publico ni procese patrones de origen no confiable. Dependencias runtime auditadas sin alertas; Maven no tuvo auditoria completa CVE.

No ejecutado: restauracion de backup, Compose runtime (permiso daemon), carga20usuarios/20kproductos/50kventas, disponibilidad99,5%, Firefox/Edge, evaluacion humana4/5, prueba bancaria, commit incierto inducido, idempotencia de reintentos de cobro y despliegue HTTPS. No hay defectos criticos conocidos en los flujos P0 probados; quedan los riesgos de alcance y operacion en MVP_ALCANCE.md. No se inventa resultado del caso HU-029 ganancia18000 porque finanzas no esta implementada.

Postman CLI 1.83.1: `postman status` indico sin workspace inicializado ni cuenta; la peticion local no requirio cuenta. No se inicializo/publico workspace ni se hizo push remoto.
