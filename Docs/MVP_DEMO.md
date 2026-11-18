# Demostracion reproducible

## Preparacion

Seguir instalacion README. Mongo rs0 PRIMARY, backend con SEED_DEMO=true y Vite en http://127.0.0.1:5173. El seed contiene productos DEMO identificados como ficticios; reiniciar no repone su stock. Usar un codigo nuevo para repetir el recorrido, por ejemplo DEMO-RECORRIDO-02.

## Recorrido principal

1. Entrar como admin@smartbiz.local, password DemoSmartBiz2026!. Mostrar Inventario con precios/stock procedentes de Mongo.
2. Crear categoria `Recorrido noviembre` (si existe reutilizarla; duplicarla debe mostrar error).
3. Crear producto `Cuaderno demostracion`, codigo `DEMO-RECORRIDO-01`, categoria anterior, precio 1500 COP, existencias 10.
4. Buscar su codigo y filtrar categoria. Comprobar 10 unidades. Intentar otro producto con mismo codigo: se rechaza sin duplicar. Cerrar el dialogo.
5. Cerrar sesion; entrar como caja@smartbiz.local con la misma password local. No aparecen altas de catalogo, pero si Caja e Historial.
6. Abrir Caja, buscar codigo, agregar producto. Cambiar cantidad de 3 a 2. Total estimado 3000 COP. Ingresar efectivo 5000: cambio 2000. Revisar y confirmar pago.
7. Mostrar recibo con producto, cantidad 2, precio 1500, total 3000, fecha, cajero, efectivo y cambio. Descargar TXT o imprimir (Guardar como PDF si se desea).
8. Cerrar recibo, abrir Inventario y buscar codigo: quedan 8. Abrir Historial, recuperar el recibo. Recargar navegador: conserva informacion.
9. Detener y reiniciar backend y Mongo sin borrar volumen; entrar otra vez. Stock 8 y venta deben mantenerse. Las sesiones no sobreviven intencionalmente al reinicio.
10. Entrar como bodega@smartbiz.local: puede consultar/crear productos, no Caja ni Historial ni categorias. La autorizacion no depende de ocultar botones; las pruebas HTTP cubren rechazo directo.

## Escenarios negativos

No se guarda producto sin categoria, precio positivo o cantidad no negativa. Una busqueda sin coincidencias muestra vacio. Cantidad mayor al stock bloquea cobro en UI; servidor vuelve a comprobarlo frente a concurrencia. Efectivo insuficiente bloquea confirmacion y servidor rechaza sin cambios parciales. Cerrar confirmacion antes de cobrar no descuenta existencias. No hay boton de borrar ventas: anulacion/devolucion esta aplazada.

## Evidencia automatica

`npm run test:e2e` reproduce altas, filtro, cambio de cantidad, pago, descarga, historial, stock, logout, login incorrecto y roles; incluye viewport 390x844. Requiere backend/Mongo activos. `npm run test:persistence` con MONGOD_BINARY reproduce reinicio completo sobre una base separada y detiene solamente sus propios procesos.

No presentar el recibo como factura fiscal ni esta demostracion interna como validacion con empresa real, aceptacion del PO o prediccion inteligente.
