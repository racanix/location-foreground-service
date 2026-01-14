# location-foreground-service

location

## Install

```bash
npm install location-foreground-service
npx cap sync
```

## API

<docgen-index>

* [`startTracking(...)`](#starttracking)
* [`stopTracking()`](#stoptracking)
* [`isTracking()`](#istracking)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### startTracking(...)

```typescript
startTracking(options: StartTrackingOptions) => Promise<StartTrackingResult>
```

Inicia el servicio nativo y habilita el reporte continuo de ubicación.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#starttrackingoptions">StartTrackingOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#trackingstate">TrackingState</a>&gt;</code>

--------------------


### stopTracking()

```typescript
stopTracking() => Promise<StopTrackingResult>
```

Detiene el servicio nativo y limpia los recursos asociados.

**Returns:** <code>Promise&lt;<a href="#trackingstate">TrackingState</a>&gt;</code>

--------------------


### isTracking()

```typescript
isTracking() => Promise<IsTrackingResult>
```

Verifica si el servicio nativo sigue activo.

**Returns:** <code>Promise&lt;<a href="#trackingstate">TrackingState</a>&gt;</code>

--------------------


### Interfaces


#### TrackingState

Estado mínimo devuelto por los métodos públicos del plugin.

| Prop          | Type                 | Description                                     |
| ------------- | -------------------- | ----------------------------------------------- |
| **`running`** | <code>boolean</code> | Indica si el servicio nativo está ejecutándose. |


#### StartTrackingOptions

Configuración requerida para iniciar el servicio nativo de rastreo.
Todas las propiedades son multiplataforma y deben mantenerse sincronizadas con Android/iOS.

| Prop                          | Type                                                            | Description                                                                          |
| ----------------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| **`endpoint`**                | <code>string</code>                                             | URL absoluta del endpoint que recibirá las posiciones.                               |
| **`headers`**                 | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Encabezados adicionales enviados en cada petición HTTP.                              |
| **`metadata`**                | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Datos estáticos adjuntos a cada payload (por ejemplo, ids de usuario o dispositivo). |
| **`minUpdateIntervalMillis`** | <code>number</code>                                             | Intervalo deseado entre lecturas (ms). Valor por defecto: 10000.                     |
| **`fastestIntervalMillis`**   | <code>number</code>                                             | Intervalo mínimo absoluto aceptado por el servicio (ms). Valor por defecto: 5000.    |
| **`minUpdateDistanceMeters`** | <code>number</code>                                             | Distancia mínima en metros para disparar un nuevo evento. Valor por defecto: 5.      |
| **`notificationTitle`**       | <code>string</code>                                             | Texto mostrado como título de la notificación persistente.                           |
| **`notificationBody`**        | <code>string</code>                                             | Texto mostrado como cuerpo de la notificación persistente.                           |
| **`retryDelayMillis`**        | <code>number</code>                                             | Tiempo de espera antes de reintentar envíos fallidos (ms). Valor por defecto: 5000.  |
| **`queueCapacity`**           | <code>number</code>                                             | Capacidad máxima de la cola en memoria. Valor por defecto: 32.                       |
| **`accuracy`**                | <code><a href="#locationaccuracy">LocationAccuracy</a></code>   | Prioridad deseada para el proveedor de ubicación.                                    |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>


#### LocationAccuracy

<code>'high' | 'balanced'</code>


#### StartTrackingResult

<code><a href="#trackingstate">TrackingState</a></code>


#### StopTrackingResult

<code><a href="#trackingstate">TrackingState</a></code>


#### IsTrackingResult

<code><a href="#trackingstate">TrackingState</a></code>

</docgen-api>
