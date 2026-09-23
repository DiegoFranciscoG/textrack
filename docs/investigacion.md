# Investigación y fuentes

Consulta realizada el **2026-09-23**. Cada regla de negocio del sistema apunta a una fila de esta tabla (columna `#`).
Cuando una regla no se pudo verificar en una fuente oficial o revisada por pares se marca como **SUPUESTO** (sección final).

## 1. Fuentes

| # | Fuente (oficial / confiable) | URL | Consultada | Qué se tomó de aquí |
|---|---|---|---|---|
| 1 | UNEP (2023). *Sustainability and Circularity in the Textile Value Chain – A Global Roadmap*. ISBN 978-92-807-4034-9 | https://www.unep.org/resources/publication/sustainability-and-circularity-textile-value-chain-global-roadmap | 2026-09-23 | Cadena de valor textil: preparación de fibra → hilado → tejido (plano/punto) → blanqueo/teñido/acabado → ensamble (corte y confección) → distribución. Delimita el alcance del MES: solo las etapas de **corte, confección y empaque**; el tejido y el teñido llegan como **rollos con lote de teñido**. |
| 2 | OIT – Kanawaty, G. (ed.) (1992). *Introduction to Work Study*, 4.ª ed. revisada. ISBN 92-2-107108-1 | https://webapps.ilo.org/public/libdoc/ilo/1992/92B09_329_engl.pdf | 2026-09-23 | *Standard time* = tiempo básico (observado × factor de ritmo) + suplementos por descanso y contingencias, expresado en **minutos estándar** (SAM). **Rendimiento = minutos estándar producidos / minutos reloj × 100** (cap. 27). El trabajador debe conocer su ganancia por incentivo **al día siguiente** → reporte de destajo diario. |
| 3 | ISO 22400-2:2014 (+Amd 1:2017) *KPIs for manufacturing operations management – Part 2: Definitions and descriptions* (ficha oficial) | https://www.iso.org/standard/54497.html | 2026-09-23 | Norma vigente (etapa 90.92 «to be revised»; ISO/DIS 22400-2 en desarrollo). Define los KPI por *work unit* (IEC 62264). El texto es de pago: las fórmulas se toman de la fuente #4. |
| 4 | Kang, N., Zhao, C., Li, J., Horst, J. A. (2016). *A hierarchical structure of key performance indicators for operation management and continuous improvement in production systems*. Int. J. Prod. Res. 54(21), 6333-6350. DOI 10.1080/00207543.2015.1136082 (publicación NIST) | https://tsapps.nist.gov/publication/get_pdf.cfm?pub_id=919754 | 2026-09-23 | Elementos de tiempo de ISO 22400-2: POT = PBT + PDOT; PRI; APT; ADOT. **A = APT/PBT**, **E = PRI·PQ/APT**, **QBR = GQ/PQ**, **OEE = A·E·QBR**, **WE = APWT/APAT**. |
| 5 | ISO 2859-1:2026 (ed. 3, ene-2026) *Sampling procedures for inspection by attributes – Part 1: AQL* (ficha oficial) | https://www.iso.org/standard/2859-1 | 2026-09-23 | La edición 1999 (+Amd 1:2011) fue **retirada**; la ed. 3 añade muestreo *skip-lot* y guía actualizada. Esquemas de muestreo simple, doble y múltiple indexados por AQL. |
| 6 | U.S. DoD (1989). **MIL-STD-105E** *Sampling Procedures and Tables for Inspection by Attributes* — dominio público («Distribution Statement A»), origen de las tablas de ISO 2859-1 | https://www.woodencrates.org/standards/MIL-STD-105.pdf | 2026-09-23 | **Tabla I** (letra código por tamaño de lote y nivel S-1…S-4, I, II, III) y **Tabla II-A** (plan simple, inspección normal: n, Ac, Re). Verificadas contra el escaneo del original (págs. 13-14). Regla 4.9.3: al seguir una flecha se usa el tamaño de muestra de la nueva letra; si n ≥ lote → inspección 100 %. |
| 7 | ASTM D5430-26 *Standard Test Methods for Visually Inspecting and Grading Fabrics* (ficha oficial) | https://www.astm.org/Standards/D5430.htm | 2026-09-23 | Edición vigente **D5430-26** (23-mar-2026). §1.2: la aceptación de rollos y envíos se hace **según lo acordado entre comprador y vendedor** → el umbral de aceptación es un parámetro configurable. |
| 8 | Das Gupta, A. et al. (2024). *An approach to automatic fault detection in four-point system for knitted fabric…* **Heliyon** 10, e35931 (revisado por pares). DOI 10.1016/j.heliyon.2024.e35931 | https://pmc.ncbi.nlm.nih.gov/articles/PMC11639326/ | 2026-09-23 | Sistema de 4 puntos (ASTM D5430): ≤ 3 in → 1 punto; 3–6 in → 2; 6–9 in → 3; > 9 in → 4; **agujero → 4**. **Puntos/100 yd² = puntos × 36 × 100 / (largo yd × ancho in)**. Umbrales de ejemplo: A ≤ 20, B 21–28, rechazo > 28 (tejido de punto). |
| 9 | Código del Trabajo del Ecuador, Codificación 17 (R.O. S. 167, 16-dic-2005; últ. mod. 22-jun-2020) — publicado por el Consejo de Educación Superior (LOTAIP) | https://www.ces.gob.ec/lotaip/2020/Junio/Literal_a2/C%C3%B3digo%20del%20Trabajo.pdf | 2026-09-23 | **Art. 16**: destajo = remuneración por pieza/unidad de obra sin considerar el tiempo. **Art. 47**: 8 h diarias / 40 h semanales. **Art. 50**: 5 jornadas por semana; sábado y domingo descanso. **Art. 53**: descanso semanal del destajero = promedio de lunes a viernes, **nunca inferior a la remuneración mínima**. **Art. 55 num. 3**: unidades hechas después de las 8 h obligatorias con recargo del **50 %** (hasta 24h00) o **100 %** (24h00–06h00) sobre el valor unitario diurno. **Art. 55 num. 4**: trabajo en sábado o domingo con **100 %** de recargo. **Art. 81**: ninguna remuneración inferior a los mínimos legales. **Art. 82**: jornada parcial → remuneración proporcional a la jornada completa. **Art. 83**: salarios se pagan en plazo máximo de una semana. **Art. 117**: el SBU se fija anualmente. |
| 10 | Ministerio del Trabajo — Acuerdo tripartito SBU 2026 (Acuerdo Ministerial MDT-2025-195, 15-dic-2025) | https://www.trabajo.gob.ec/despues-de-casi-una-decada-hay-consenso-gobierno-empleadores-y-trabajadores-acuerdan-fijar-el-salario-basico-unificado-de-2026-en-usd-482-no-hay-imposicion-hay-union/ | 2026-09-23 | **SBU 2026 = USD 482,00** (+USD 12), vigente desde el 1-ene-2026 (R.O. S. 187, 18-dic-2025). Se guarda como parámetro por año (`payroll_parameters`), no como constante en el código. |
| 11 | Ley Orgánica de Protección de Datos Personales (LOPDP), R.O. S. 459, 26-may-2021 | https://www.telecomunicaciones.gob.ec/wp-content/uploads/2021/06/Ley-Organica-de-Datos-Personales.pdf | 2026-09-23 | Principio de **minimización**: de los operarios solo se guarda código, nombre y línea (sin cédula, dirección ni teléfono). Los datos de demo son ficticios. |
| 12 | IETF httpapi WG — *The Idempotency-Key HTTP Header Field*, draft-07 (15-oct-2025) | https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/ | 2026-09-23 | Patrón de clave de idempotencia generada por el cliente para POST: repetir la misma clave devuelve el mismo resultado sin crear otro recurso. Se aplica con un `clientReadingId` (UUID) por lectura. |
| 13 | IETF RFC 2104 *HMAC: Keyed-Hashing for Message Authentication* y NIST FIPS 198-1 | https://www.rfc-editor.org/rfc/rfc2104 | 2026-09-23 | Firma de tickets QR con **HMAC-SHA-256** y clave solo en el servidor; comparación en tiempo constante. |
| 14 | Android Developers — *Build an offline-first app* | https://developer.android.com/topic/architecture/data-layer/offline-first | 2026-09-23 | La base local (Room) es la fuente de verdad; escrituras «lazy» encoladas y sincronizadas con **WorkManager** (`enqueueUniqueWork`, restricción de red, *backoff* exponencial). |
| 15 | Google ML Kit — *Scan barcodes with ML Kit on Android* | https://developers.google.com/ml-kit/vision/barcode-scanning/android | 2026-09-23 | Modelo **empaquetado** `com.google.mlkit:barcode-scanning:17.3.0` funciona **sin conexión**; `ImageAnalysis.Analyzer` de CameraX; restringir a `FORMAT_QR_CODE`; API mínima 23. |
| 16 | Spring — generaciones y soporte de Spring Boot (API oficial) | https://api.spring.io/projects/spring-boot/generations | 2026-09-23 | **Spring Boot 3.5.x terminó su soporte OSS el 30-jun-2026**. 4.1.x: soporte OSS hasta 31-jul-2027 → se usa **Spring Boot 4.1.1** (última estable en Maven Central). |
| 17 | Android Developers — *Android Gradle Plugin release notes* | https://developer.android.com/build/releases/gradle-plugin | 2026-09-23 | AGP 9.4 requiere Gradle ≥ 9.6 y JDK 17; Kotlin integrado en AGP 9. |
| 18 | Render — *Deploy for Free* | https://render.com/docs/free | 2026-09-23 | Web service gratis: se duerme tras 15 min sin tráfico, 750 h/mes, **soporta WebSocket** (los mensajes reinician el temporizador), sin disco persistente. |
| 19 | Neon — *Postgres version policy* | https://neon.com/docs/postgresql/postgres-version-policy | 2026-09-23 | Neon soporta PostgreSQL 14–18 → se usa **PostgreSQL 18** en local y en Neon. |
| 20 | OWASP API Security Top 10 (2023) | https://owasp.org/API-Security/editions/2023/en/0x11-t10/ | 2026-09-23 | Controles aplicados: autorización por rol en cada endpoint (API1/API5), autenticación robusta y *rate limiting* en login (API2/API4), validación de propiedades en DTO (API3), inventario con OpenAPI (API9). |

### Versiones verificadas (repositorios oficiales, 2026-09-23)

| Componente | Versión | Fuente |
|---|---|---|
| Java (LTS) | 25 (Temurin 25.0.3) | Docker Hub `eclipse-temurin` |
| Spring Boot | 4.1.1 | Maven Central + api.spring.io (#16) |
| Flyway | 13.7.0 (gestionada por Spring Boot) | Maven Central |
| Testcontainers | 2.0.5 | Maven Central |
| PostgreSQL | 18 | Docker Hub / Neon (#19) |
| Angular | 22.2.0 | npm |
| Node.js LTS | 24 (Krypton) | nodejs.org/dist/index.json |
| Kotlin | 2.4.20 | Maven Central |
| Android Gradle Plugin | 9.4.1 | Google Maven (#17) |
| Gradle | 9.7.1 | services.gradle.org |
| Compose BOM | 2026.09.00 | Google Maven |
| CameraX | 1.6.2 | Google Maven |
| ML Kit barcode-scanning | 17.3.0 | Google Maven (#15) |
| Room / WorkManager | 2.8.5 / 2.12.0 | Google Maven |

## 2. Reglas derivadas

| Regla | Detalle | Fuente |
|---|---|---|
| R1 · Alcance del proceso | El MES arranca en la recepción de **rollos** (con lote de teñido, metros y ancho) y cubre corte → confección → empaque. | #1 |
| R2 · SAM | Cada operación de un estilo tiene un SAM (minutos estándar, > 0, 4 decimales). | #2 |
| R3 · Eficiencia de operario | `eficiencia = Σ(SAM × piezas) / minutos de asistencia × 100`. | #2 |
| R4 · OEE por línea | `A = APT/PBT`, `E = Σ(SAM × piezas)/APT`, `Q = GQ/PQ`, `OEE = A × E × Q`, con PBT = minutos de asistencia − paros planificados y APT = PBT − paros no planificados (estación operario-máquina como *work unit*). | #3, #4 |
| R5 · AQL | Letra código por Tabla I; plan (n, Ac, Re) por Tabla II-A con reglas de flechas; lote aceptado si defectuosos ≤ Ac, rechazado si ≥ Re. AQL soportados: 0,10 – 6,5; niveles S-1…S-4, I, II, III. | #5, #6 |
| R6 · 4 puntos | Puntos por defecto según longitud (≤ 75 mm → 1; ≤ 150 mm → 2; ≤ 230 mm → 3; > 230 mm → 4; agujero → 4); puntos/100 yd² con la fórmula de #8; umbral configurable por acuerdo comprador-vendedor. Un rollo rechazado no puede usarse en un corte. | #7, #8 |
| R7 · Destajo | Pago = Σ piezas × tarifa vigente de la operación en la fecha. | #9 (Art. 16) |
| R8 · Recargos | Piezas registradas después de 8 h de jornada: +50 %; entre 00h00 y 06h00: +100 %; sábado o domingo: +100 %. | #9 (Art. 55) |
| R9 · Piso SBU | Alerta si el pago ordinario del día < **SBU/30 × horas ordinarias/8**; se calcula el complemento para llegar al piso. | #9 (Arts. 53, 81, 82), #10 |
| R10 · Descanso semanal | Pago de sábado y domingo del destajero = 2 × promedio lunes–viernes, mínimo 2 × SBU/30. | #9 (Art. 53) |
| R11 · Idempotencia | Un ticket se registra una sola vez (restricción única en BD); cada lectura trae un `clientReadingId` único que hace idempotente el reintento de sincronización. | #12, #14 |
| R12 · Tickets firmados | Contenido del QR firmado con HMAC-SHA-256; el servidor rechaza tickets con firma inválida (falsificados). | #13 |
| R13 · Minimización de datos | Operarios sin cédula ni datos de contacto; seeds ficticios. | #11 |

## 3. Supuestos (no verificados en fuente oficial) — revisar

- **SUPUESTO S1 · Mes comercial de 30 días**: el piso diario se prorratea como `SBU / 30` por día trabajado de 8 h (`SBU / 240` por hora). Es la convención usada en liquidaciones laborales en Ecuador; coherente con el Art. 53 (los 2 días de descanso se pagan aparte), pero no encontré un artículo que la fije de forma literal.
- **SUPUESTO S2 · Tablas AQL de la ed. 2026**: ISO 2859-1:2026 es de pago; asumo que la Tabla 1 y la Tabla 2-A del plan simple normal conservan los valores de la ed. 1999 / MIL-STD-105E (la ficha oficial solo anuncia *skip-lot* y guía actualizada). Los planes se guardan con la edición de origen para poder actualizarlos.
- **SUPUESTO S3 · Umbral de 4 puntos por defecto = 40 puntos/100 yd²** (valor habitual en la industria para tejido plano). ASTM D5430 deja el umbral al acuerdo comprador-vendedor; la fuente revisada por pares (#8) usa 28 para tejido de punto. Es configurable (`APP_FABRIC_MAX_POINTS`).
- **SUPUESTO S4 · Máximo 4 puntos por yarda lineal**: regla citada por la industria para ASTM D5430 que no pude leer en el texto oficial (de pago). Se aplica al calcular el total del rollo.
- **SUPUESTO S5 · Equivalencias métricas del 4 puntos** (75 / 150 / 230 mm) redondeadas desde 3 / 6 / 9 pulgadas.
- **SUPUESTO S6 · Hora del escaneo = hora de terminación** de la operación del bulto (base para recargos del Art. 55).
- **SUPUESTO S7 · Calidad (Q) estimada por muestreo**: `Q = 1 − unidades defectuosas / unidades muestreadas` de las inspecciones AQL del período; sin inspecciones se reporta Q = 1 marcado como «sin datos».
- **SUPUESTO S8 · Evaluación AQL con dos clases**: n = máximo de los tamaños de muestra de los planes de defectos mayores y menores; cada clase se decide con su propio Ac/Re; cualquier defecto crítico rechaza el lote.
- **SUPUESTO S9 · Tolerancia de sobrecorte del 5 %** sobre la cantidad pedida por talla y color (práctica de planta, configurable).
- **SUPUESTO S10 · Un bulto sale de un solo rollo** (no se mezclan lotes de teñido en un bulto) para evitar diferencias de tono; es práctica de la industria, no norma.
- **SUPUESTO S11 · Feriados**: el recargo del Art. 55 num. 4 se aplica a sábados y domingos; los feriados del Art. 65 quedan en el roadmap (cambian por decreto cada año).
