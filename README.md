# EUDI Verifier — Backend

API REST (Spring Boot 4 / Java 21) para la verificación de edad bajo el estándar **eIDAS 2.0**, **OpenID4VP** y **SD-JWT**, orientada al cumplimiento del **EU KIDS Act**.

## Características

- Generación de *challenges* OpenID4VP con nonce de un solo uso y TTL.
- Validación criptográfica de SD-JWT contra el JWKS/Trust Registry del emisor.
- Registro de auditoría de cada verificación en base de datos.
- Perfiles de Spring (`dev`/`prod`) con bypass de simulación aislado.
- Documentación OpenAPI/Swagger.
- Health check para despliegues.

## Stack

- Spring Boot 4.1.1, Java 21
- Spring Security, Spring Data JPA, Validation, Actuator
- PostgreSQL (Neon en producción)
- Nimbus JOSE + JWT
- springdoc OpenAPI 3

## Estructura

```
src/main/java/com/eudi/verifier/
  config/        # CORS, seguridad y OpenAPI
  controller/    # Endpoints REST
  dto/           # Requests/Responses
  model/         # Entidades y enums
  repository/    # Repositorios JPA
  service/       # Lógica de negocio (nonce, OID4VP, SD-JWT)
```

## Endpoints

Base: `/api/v1/verify`

| Método | Ruta | Descripción |
| --- | --- | --- |
| `POST` | `/challenge` | Genera un nonce y la URI `openid4vp://` |
| `POST` | `/submission` | Valida el `vpToken` enviado por la wallet |

Ejemplo de submission:

```json
{
  "nonce": "abc123",
  "vpToken": "eyJhbGciOiJFUzI1NiJ9..."
}
```

Respuesta:

```json
{
  "status": "SUCCESS",
  "isAgeVerified": true,
  "sessionToken": "eudi_sess_...",
  "message": "Verificación de edad (>15 años) completada con éxito.",
  "messageCode": "verification.success"
}
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Perfiles y seguridad

- **`dev`**: habilita el bypass de tokens mock para el simulador de la wallet. **Nunca usar en producción.**
- **Sin perfil / `prod`**: solo se acepta validación criptográfica real. Un token mock se rechaza siempre.

Variables de entorno (ver `.env.example`):

| Variable | Descripción |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` o `prod` |
| `SPRING_DATASOURCE_URL` | JDBC de Neon (con `?sslmode=require`) |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la base de datos |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de la base de datos |
| `SPRING_JPA_DDL_AUTO` | `update` (por defecto) |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes permitidos, separados por comas |
| `PORT` | Puerto HTTP (Render lo inyecta) |

## Desarrollo local

1. Crea `src/main/resources/application-dev.properties` (no se sube a git) con tus credenciales locales:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/eudi_verifier_db
spring.datasource.username=postgres
spring.datasource.password=TU_PASSWORD
spring.jpa.show-sql=true
app.cors.allowed-origins=http://localhost:4200
```

2. Ejecuta con el perfil `dev`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

En Windows:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

## Tests

```bash
./mvnw test
```

## Despliegue en Render

El repo incluye `Dockerfile` multi-stage (Maven + JRE 21) y `.dockerignore`.

1. Crea la base de datos en **Neon** y copia la cadena JDBC (`?sslmode=require`).
2. En **Render**, crea un **Web Service** desde el repo con runtime **Docker**.
3. Define las variables de entorno: `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_*` y `APP_CORS_ALLOWED_ORIGINS` con el dominio de Vercel.
4. Render detecta `PORT` automáticamente y usa `/actuator/health` como health check.

## Seguridad y secretos

- `application-dev.properties`, `.env` y `.env.*` están en `.gitignore`.
- No se suben contraseñas ni claves al repositorio.
- El bypass de desarrollo está aislado por perfil y desactivado por defecto.
