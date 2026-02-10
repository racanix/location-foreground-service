# location-foreground-service

location

## Install

```bash
npm install location-foreground-service
npx cap sync
```

## Check and Debug

```bash
npm run verify:android
npm run verify:ios
```

check Kotlin rapido:

```bash
./gradlew compileDebugKotlin
```

## Build y Pack

```
npm run build
npm pack
```

## API

<docgen-index>

* [`startTracking(...)`](#starttracking)
* [`stopTracking()`](#stoptracking)
* [`isTracking()`](#istracking)
* [`addAlert(...)`](#addalert)
* [`removeAlert(...)`](#removealert)
* [`existsAlert(...)`](#existsalert)
* [`getAllAlerts()`](#getallalerts)
* [`existAlertType(...)`](#existalerttype)
* [`getAlertCount()`](#getalertcount)
* [`clearAllAlerts()`](#clearallalerts)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

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


### addAlert(...)

```typescript
addAlert(alert: Alert) => Promise<{ success: boolean; }>
```

Agrega una nueva alerta al sistema de monitoreo.
Retorna true si se agregó exitosamente, false si ya existía.

| Param       | Type                                    |
| ----------- | --------------------------------------- |
| **`alert`** | <code><a href="#alert">Alert</a></code> |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### removeAlert(...)

```typescript
removeAlert(options: { id: string; }) => Promise<{ success: boolean; }>
```

Remueve una alerta existente del sistema de monitoreo.
Retorna true si se removió exitosamente, false si no existía.

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

--------------------


### existsAlert(...)

```typescript
existsAlert(options: { id: string; }) => Promise<{ alert: Alert | null; }>
```

Verifica si existe una alerta con el ID proporcionado.
Retorna el objeto de alerta si existe, o null en caso contrario.

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

**Returns:** <code>Promise&lt;{ alert: <a href="#alert">Alert</a> | null; }&gt;</code>

--------------------


### getAllAlerts()

```typescript
getAllAlerts() => Promise<{ alerts: Alert[]; }>
```

Obtiene todas las alertas activas en el sistema.
Retorna una lista de objetos de alerta.

**Returns:** <code>Promise&lt;{ alerts: Alert[]; }&gt;</code>

--------------------


### existAlertType(...)

```typescript
existAlertType(options: { type: AlertType; }) => Promise<{ exists: boolean; }>
```

Verifica si existe al menos una alerta del tipo especificado.
Retorna true si existe, false en caso contrario.

| Param         | Type                                                       |
| ------------- | ---------------------------------------------------------- |
| **`options`** | <code>{ type: <a href="#alerttype">AlertType</a>; }</code> |

**Returns:** <code>Promise&lt;{ exists: boolean; }&gt;</code>

--------------------


### getAlertCount()

```typescript
getAlertCount() => Promise<{ count: number; }>
```

Obtiene el número total de alertas activas en el sistema.
Retorna el conteo como un número entero.

**Returns:** <code>Promise&lt;{ count: number; }&gt;</code>

--------------------


### clearAllAlerts()

```typescript
clearAllAlerts() => Promise<{ success: boolean; }>
```

Remueve todas las alertas activas del sistema.
Retorna true si se removieron exitosamente.

**Returns:** <code>Promise&lt;{ success: boolean; }&gt;</code>

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

| Prop                           | Type                                                            | Description                                                                          |
| ------------------------------ | --------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| **`endpoint`**                 | <code>string</code>                                             | URL absoluta del endpoint que recibirá las posiciones.                               |
| **`alertTerminationEndpoint`** | <code>string</code>                                             | URL completa del endpoint para finalizar la alerta                                   |
| **`headers`**                  | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Encabezados adicionales enviados en cada petición HTTP.                              |
| **`metadata`**                 | <code><a href="#record">Record</a>&lt;string, string&gt;</code> | Datos estáticos adjuntos a cada payload (por ejemplo, ids de usuario o dispositivo). |
| **`minUpdateIntervalMillis`**  | <code>number</code>                                             | Intervalo deseado entre lecturas (ms). Valor por defecto: 10000.                     |
| **`fastestIntervalMillis`**    | <code>number</code>                                             | Intervalo mínimo absoluto aceptado por el servicio (ms). Valor por defecto: 5000.    |
| **`minUpdateDistanceMeters`**  | <code>number</code>                                             | Distancia mínima en metros para disparar un nuevo evento. Valor por defecto: 5.      |
| **`notificationTitle`**        | <code>string</code>                                             | Texto mostrado como título de la notificación persistente.                           |
| **`notificationBody`**         | <code>string</code>                                             | Texto mostrado como cuerpo de la notificación persistente.                           |
| **`retryDelayMillis`**         | <code>number</code>                                             | Tiempo de espera antes de reintentar envíos fallidos (ms). Valor por defecto: 5000.  |
| **`queueCapacity`**            | <code>number</code>                                             | Capacidad máxima de la cola en memoria. Valor por defecto: 32.                       |
| **`accuracy`**                 | <code><a href="#locationaccuracy">LocationAccuracy</a></code>   | Prioridad deseada para el proveedor de ubicación.                                    |


#### Alert

Representa una alerta activa que el servicio debe monitorear.

| Prop                 | Type                                                      | Description                                           |
| -------------------- | --------------------------------------------------------- | ----------------------------------------------------- |
| **`id`**             | <code>string</code>                                       | Identificador único de la alerta.                     |
| **`type`**           | <code><a href="#alerttype">AlertType</a></code>           | Tipo de alerta.                                       |
| **`targetLocation`** | <code><a href="#targetlocation">TargetLocation</a></code> | Ubicación objetivo asociada a esta alerta, si aplica. |


#### TargetLocation

Ubicación objetivo para detener el servicio cuando el usuario llegue al destino.

| Prop            | Type                | Description                                                     |
| --------------- | ------------------- | --------------------------------------------------------------- |
| **`latitude`**  | <code>number</code> | Latitud del destino.                                            |
| **`longitude`** | <code>number</code> | Longitud del destino.                                           |
| **`range`**     | <code>number</code> | Rango en metros para considerar llegada. Valor por defecto: 10. |


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


### Enums


#### AlertType

| Members                | Value                           |
| ---------------------- | ------------------------------- |
| **`JOURNEY`**          | <code>'JOURNEY'</code>          |
| **`QUICK`**            | <code>'QUICK'</code>            |
| **`REQUEST_LOCATION`** | <code>'REQUEST_LOCATION'</code> |
| **`DEFAULT`**          | <code>'DEFAULT'</code>          |

</docgen-api>
