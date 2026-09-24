# Política de seguridad

## Reportar una vulnerabilidad
No abras un issue público. Escríbeme por GitHub (perfil DiegoFranciscoG) con los pasos para reproducir el problema. Respondo en un máximo de 7 días.

## Prácticas aplicadas en este proyecto
- Secretos solo por variables de entorno (`.env` no se versiona; ver `.env.example`). La API no arranca si falta `JWT_SECRET`, `TICKET_HMAC_SECRET` o la conexión a la base de datos.
- Autorización deny-by-default por rol en cada ruta; JWT HS256 de vida corta y refresh tokens rotativos con detección de reutilización.
- Contraseñas con BCrypt; rate limiting en el login por IP, por IP + usuario y por cuenta.
- Los logs no registran contraseñas, tokens ni valores de datos personales.
- Validación de entradas (Bean Validation), límite de 1 MB por petición y errores RFC 9457 sin detalles internos.
- Tickets QR firmados con HMAC-SHA-256; los tickets con firma inválida se rechazan y quedan auditados.
- CORS y orígenes de WebSocket explícitos; cabeceras CSP, HSTS, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`.
- Contenedores con versión fija, usuario no root y healthcheck.
- Escaneo de secretos con gitleaks en cada push y pull request; Dependabot para todas las dependencias; GitHub Actions fijadas por SHA con permisos mínimos.
- Datos de demostración ficticios; minimización de datos personales (LOPDP).
