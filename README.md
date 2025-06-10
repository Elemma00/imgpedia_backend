# IMGpedia Backend

IMGpedia es un conjunto de datos enlazados (Linked Dataset) que incorpora información visual de las imágenes del conjunto de Wikimedia Commons: reúne descriptores del contenido visual de 15 millones de imágenes, 450 millones de relaciones de similitud visual entre esas imágenes, enlaces a metadatos de imágenes desde DBpedia Commons y enlaces a los recursos de DBpedia asociados a imágenes individuales. Permite realizar consultas visuo-semánticas sobre las imágenes.

## Características principales

- **Carga y limpieza de datos RDF**: Procesa archivos `.ttl`, `.nt`, y otros formatos RDF, limpiando y validando los datos antes de cargarlos en la base de datos.
- **Validación robusta**: Usa Apache Jena y herramientas como `tdb2.xloader` para validar archivos N-Triples y asegurar la integridad de los datos.
- **API RESTful**: Permite consultas SPARQL y operaciones de administración de usuarios a través de endpoints seguros.
- **Gestión de usuarios y roles**: Soporta autenticación, autorización y administración de usuarios con roles (USER, ADMIN, SUPERADMIN).
- **Procesamiento eficiente**: Utiliza procesamiento concurrente y manejo de archivos grandes, incluyendo soporte para archivos comprimidos y cargas masivas.
- **Logs y seguimiento**: Incluye un sistema de logging detallado para seguimiento de procesos y errores.

## Estructura del proyecto

- `controllers/` — Endpoints REST para carga de datos, consultas SPARQL y administración.
- `dataload/` — Lógica de procesamiento, limpieza, validación y carga de archivos RDF.
- `models/` — Entidades de dominio, DTOs y modelos de usuario.
- `services/` — Lógica de negocio para usuarios, carga RDF y consultas.
- `utils/` — Utilidades para logs, validaciones y manejo de archivos.
- `configuration/` — Configuración de seguridad, beans y parámetros de la aplicación.
- `resources/` — Archivos de configuración y plantillas.

## Flujo típico de datos

1. **Carga**: El usuario sube archivos RDF mediante la API.
2. **Limpieza y validación**: Los archivos se limpian (normalización de IRIs, filtrado de triples problemáticos) y se validan con Jena y xloader.
3. **Carga en TDB**: Los datos limpios se cargan en una base de datos Jena TDB optimizada para consultas SPARQL.
4. **Consulta**: Los usuarios pueden realizar consultas SPARQL a través de la API.

## Tecnologías principales

- Java 17+
- Spring Boot
- Apache Jena (TDB1/TDB2, RIOT)
- Spring Security
- Swagger/OpenAPI

## Uso rápido

1. Configura las rutas de datos en las variables de entorno o archivos de propiedades.
2. Ejecuta la aplicación (`mvn spring-boot:run`).
3. Usa la API para cargar archivos RDF y realizar consultas SPARQL.

## Seguridad

- Endpoints protegidos con JWT y roles.
- Solo usuarios autorizados pueden cargar datos o administrar usuarios.

---

**Contacto:**  
Para dudas o contribuciones, contacta a los desarrolladores del proyecto ImgPedia.