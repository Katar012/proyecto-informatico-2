# SmartBiz: analisis del MVP

Fecha: 2026-11-18. Estado: decision tecnica para implementacion local solicitada por el usuario, no aprobacion academica del PO.

## Investigacion realizada

Repositorio inicialmente limpio en main, commit 2d891a7, sin codigo. Se inspecciono el arbol completo, README, workflow branch-flow.yml y todos los DOCX mediante ZIP/XML (parrafos, tablas, relaciones, encabezados, pies y medios). No hay PDF, TXT ni otros documentos fuente en el checkout. Los PDF originales mencionados en el expediente no estan disponibles. Las referencias usan secciones/HU porque la paginacion de Word no es estable.

| Fuente | Contenido revisado |
|---|---|
| D00: 00_guia_maestra_del_expediente.docx | Estados de evidencia, jerarquia, entregables y convenciones |
| D01: 01_acta_constitucion_y_caso_negocio.docx | Problema, objetivos, actores, alcance, riesgos y aceptaciones |
| D02: 02_descubrimiento_procesos_y_requisitos.docx | Descubrimiento, reglas por familia y RNF propuestos |
| D03: 03_plan_gestion_agil_y_responsabilidades.docx | Responsabilidades, Kanban, Scrum y metricas |
| D04: 04_cronograma_bitacoras_y_decisiones.docx | 13 jornadas, actas, decisiones pendientes |
| D05: 05_epicas_y_hu.docx | 32 HU completas, 41 tablas, RF, RNF y cinco imagenes internas |
| D06: 06_refinamiento_priorizacion_y_plan_incremento.docx | Dependencias, INVEST, Ready/Done y MVP propuesto |
| D07: 07_diseno_calidad_y_validacion.docx | Secuencia de diseno, pruebas, puertas de calidad y validacion simulada |

Se inspeccionaron visualmente las dos imagenes Public y las cinco imagenes PNG de D05, extraidas temporalmente fuera del repositorio. C4 contexto: administrador, cajero y bodeguero. C4 contenedores/componentes/mapa: React + TypeScript + Vite, Java + Spring Boot, Spring Data MongoDB, monolito modular REST/JSON. Diagrama de datos: usuarios, turnos, productos, movimientosInventario, ventas con items/pagos/comprobante, pedidos, proveedor, ordenesCompra y gastos. No hay imagenes internas en los otros DOCX. El mockup Public es referencia visual, no compromiso de todos sus modulos. El flujo de ramas exige usuario/backend o usuario/frontend hacia backend/frontend, luego develop, luego main. Se trabaja en mvp/backend; no se hara push.

## Problema y objetivos

D01 secciones 2-6: registros dispersos dificultan conocer existencias, coordinar caja y tomar decisiones. El objetivo local es demostrar una venta cobrada que conserva detalle y responsable y descuenta inventario sin cambios parciales. No hay empresa piloto ni entrevistas externas verificadas. El expediente es un modelo para aprobacion, no evidencia de ejecucion previa.

## Actores y casos de uso

Administrador: organiza categorias/productos y consulta el negocio. Bodeguero: organiza y consulta inventario. Cajero: consulta disponibilidad y registra ventas/pagos/comprobantes. PO: decide aceptacion academica, que no se suplanta con pruebas tecnicas. Casos P0: iniciar/cerrar sesion; alta de categoria por administrador; alta y consulta filtrada de productos; armar/corregir carrito; cobrar en efectivo; consultar comprobante e historial; verificar existencias tras venta y rechazar sobreventa.

## Trazabilidad y aceptacion

P0/P1/P2 son prioridades de este corte tecnico, no sustituyen el MoSCoW original.

| Requisito | Prioridad | Fuente exacta | Criterio de aceptacion del corte |
|---|---|---|---|
| Acceso individual y cierre | P0 | D05 HU-020, RNF-001/003; D06 §6 | Cuenta activa entra, password incorrecto falla, logout revoca sesion; hash y timeout 30 min |
| Autorizacion servidor | P0 | D05 HU-022; D07 CP-HU-022-03; D06 §6 | Cajero no crea productos/categorias; bodeguero no vende; servidor rechaza URL directa |
| Catalogo y categorias | P0 | D05 HU-001 CA1-7; D06 §5-6 | Campos obligatorios, precio positivo, cantidad no negativa, codigo unico, categoria obligatoria y unica; filtro |
| Consultar existencias | P0 | D05 HU-002; D06 §6 | Buscar nombre/codigo, sin resultados explicitos, consultar stock actualizado |
| Registrar venta | P0 | D05 HU-007; D06 §6; D07 CP-HU-007-03 | Carrito modificable, total calculado servidor, rechazo de cantidades superiores a existencias |
| Pago efectivo | P0 reducido | D05 HU-008 CA1; D06 §4 | Entregado suficiente, cambio correcto, pago guardado; medios digitales aplazados |
| Descuento atomico | P0 | D05 HU-010; D07 §2 | Confirmar pago descuenta todos los items o ninguno; cancelacion previa no escribe |
| Comprobante | P0 | D05 HU-009 | Venta pagada conserva productos/precios/total/fecha/medio; historial y descarga imprimible |
| Integridad y trazabilidad | P0 | D02 §6; D05 RNF-004/012/013 | Sin stock negativo; autor y fecha, importes enteros en pesos; transaccion con rollback |
| Datos y operacion local | P0 | Solicitud actual; D07 §7 | Seed ficticio identificado, almacenamiento durable, reinicio conserva ventas |
| Entradas, ajustes, alertas, perdidas e historial | P1 | D05 HU-003 a 006 | Fuera del flujo minimo seleccionado, no presentar como terminadas |
| Pedidos, anulaciones/devoluciones, cierre | P1 | D05 HU-011 a 014 | Aplazados, se conserva historial de ventas pagadas |
| Proveedores, compras, recepciones y costos | P1 | D05 HU-015 a 018 | Aplazados |
| Empleados, desactivacion, turnos y actividad | P1 | D05 HU-021,023 a 025 | Cuentas iniciales seed; administracion posterior aplazada |
| Gastos, resumen, rotacion, ganancias y perdidas | P1 | D05 HU-026 a 030 | No mostrar dashboard financiero incompleto |
| Reposicion inteligente y comparacion/exportacion | P2 | D05 HU-019,031,032 | Sin LLM ni predicciones; necesita historial y definicion de algoritmo |

## Reglas y modelo

Producto pertenece a categoria; codigo unico normalizado. Usuario tiene cargo y estado activo. Venta pertenece a cajero y contiene instantaneas de items y pago. Movimiento enlaza producto, venta y autor. Cantidades enteras no negativas; precio y total en pesos COP enteros (decision local para evitar coma flotante). Precio de venta historico no depende de cambios posteriores del catalogo. No hay clientes ni multiempresa exigidos en este corte.

## Restricciones y RNF

Se respeta el stack explicito de diagramas D05 por encima de la preferencia Node/SQLite de la solicitud. MongoDB local necesita replica set para transacciones. Frontend accesible, responsive y espanol; validaciones de servidor, consultas seguras, errores comprensibles, secretos fuera de Git. HTTP solo loopback local; despliegue externo exige HTTPS. D05 RNF-005 a 011 proponen 20 usuarios, 20.000 productos/50.000 ventas, disponibilidad 99,5%, restauracion <4h y usabilidad 4/5: no se afirmara cumplimiento sin medir. RNF-014 propone cobertura 80%; se reportara medicion real o limite.

## Contradicciones y decisiones

D05 clasifica 23 Must/9 Should; D06 declara 24/8: prevalecen criterios de cada HU, no se corrige el original. D04 DEC-001 a 004 siguen pendientes; D06 §6 propone seis HU, no aprueba todas las Must. HU-010 depende del pago HU-008 omitido en esa lista: se incorpora efectivo y comprobante HU-009 para cerrar el flujo. No se crea estado de pago digital ficticio ni integracion bancaria. D05 define stack en imagen aunque D06/07 no lo repiten. No se interpreta la marca inteligente como machine learning. No se promete todo el ERP en este incremento.

## Riesgos y suposiciones

Disponibilidad de Docker/Mongo y descarga de dependencias; transacciones requieren replica set inicializado. Una tienda local y tres cuentas de demostracion, sin datos personales reales. Monto entero COP es suficiente para el ejemplo. El MVP se entrega como implementacion tecnica, no como acta firmada ni aceptacion PO. Gestion completa de empleados, devoluciones, compras y resultados queda explicitamente pendiente. Pruebas automaticas no reemplazan validacion humana de usabilidad ni auditoria de produccion.
