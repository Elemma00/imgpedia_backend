# IMGpedia Backend

## Índice

1. [Introducción](#introducción)  
2. [Responsabilidades principales](#responsabilidades-principales)  
3. [Estructura del proyecto](#estructura-del-proyecto)  
4. [Tecnologías principales](#tecnologías-principales)  
5. [Uso rápido](#uso-rápido)  
6. [Seguridad](#seguridad)  
7. [Contacto](#contacto)  
8. [Paginación](#paginación)  

---

## Introducción

IMGpedia Backend es el sistema encargado de procesar, limpiar, validar y cargar los datos RDF que conforman el conjunto de datos enlazados IMGpedia. Este backend gestiona la integración de información visual y semántica de millones de imágenes de Wikimedia Commons, permitiendo la administración eficiente de grandes volúmenes de datos y facilitando consultas visuo-semánticas a través de una API RESTful.

---

## Responsabilidades principales

- La conversión, limpieza y validación de archivos RDF (formatos `.ttl`, `.nt`, etc.) antes de su carga en la base de datos.
- El uso de Apache Jena y herramientas como `tdb1.xloader` para asegurar la integridad y calidad de los datos.
- La exposición de endpoints seguros para la carga de datos, consultas SPARQL y administración de usuarios y roles.
- El soporte para procesamiento concurrente y manejo eficiente de archivos grandes y cargas masivas.
- La gestión de logs y seguimiento de procesos para auditoría y depuración.

El backend de IMGpedia es el núcleo que permite que los datos visuales y semánticos estén disponibles de forma robusta y escalable para aplicaciones y usuarios finales.

---

## Estructura del proyecto

- `controllers/` — Endpoints REST para carga de datos, consultas SPARQL y administración.
- `dataload/` — Lógica de procesamiento, limpieza, validación y carga de archivos RDF.
- `models/` — Entidades de dominio, DTOs y modelos de usuario.
- `services/` — Lógica de negocio para usuarios, carga RDF y consultas.
- `utils/` — Utilidades para logs, validaciones y manejo de archivos.
- `configuration/` — Configuración de seguridad, beans y parámetros de la aplicación.
- `resources/` — Archivos de configuración y plantillas.

---

## Tecnologías principales

- [OpenJDK 21](https://openjdk.org/projects/jdk/21/)
- [Spring Boot 3.4.4](https://spring.io/projects/spring-boot)
- [Apache Jena 5.2 extendido por Ferrada et al.](https://github.com/scferrada/jena)
- [Spring Security 3.4.4](https://spring.io/projects/spring-security)
- [Docker](https://www.docker.com/)

---

## Uso rápido

1. Para desarrollo y pruebas en ambiente local, ejecutar el comando: 

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

2. Inicia sesión utilizando las credenciales usuario: `admin` y contraseña: `admin`.


3. Subir datos RDF hacia la base de datos usando el endpoint `/api/data/upload`.  

```sh
curl --location 'localhost:8081/api/data/upload' \
--form 'file=@"ruta/hacia/tu/archivo/"'
```

Puedes consultar el estado de la carga utilizando el endpoint `/api/data/status`.

4. Haz consultas SPARQL en el endpoint `api/sparql/query`


---


## Endpoints disponibles

### Consultas

| Método | Endpoint              | Descripción                              | Autenticación requerida | Parámetros requeridos                |
|--------|-----------------------|------------------------------------------|------------------------|--------------------------------------|
| POST   | `/api/sparql/query`   | Ejecutar consulta SPARQL                 | Sí                     | Body JSON: `query`                   |
| POST   | `/api/sparql/query/stop` | Detener una consulta SPARQL en ejecución | Sí                     | Body JSON: identificador de consulta |

### Gestión de usuarios

| Método | Endpoint                | Descripción                        | Autenticación requerida | Parámetros requeridos                |
|--------|------------------------|------------------------------------|------------------------|--------------------------------------|
| POST   | `/api/auth/login`      | Autenticación de usuario y obtención de JWT | No                     | `username`, `password` (body JSON)   |
| POST   | `/api/auth/register`   | Registro de nuevo usuario          | Sí (admin)                     | `username`, `password`, `email` (body JSON) |
| GET    | `/api/users`           | Listar usuarios                    | Sí (admin)             | Ninguno                              |
| POST   | `/api/users`           | Crear usuario                      | Sí (admin)             | `username`, `password`, `email`, `roles` (body JSON) |
| PUT    | `/api/users/{id}`      | Actualizar usuario                 | Sí (admin)             | `id` (path), campos a actualizar (body JSON) |
| DELETE | `/api/users/{id}`      | Eliminar usuario                   | Sí (admin)             | `id` (path)                          |

### Carga de datos

| Método | Endpoint                    | Descripción                                 | Autenticación requerida | Parámetros requeridos                |
|--------|-----------------------------|---------------------------------------------|------------------------|--------------------------------------|
| POST   | `/api/data/upload`          | Subir y cargar archivo RDF                  | Sí                     | Archivo RDF (form-data: `file`)      |
| POST   | `/api/data/upload-multiple` | Subir y cargar múltiples archivos RDF       | Sí                     | Archivos RDF (form-data: `files[]`)  |
| GET    | `/api/data/status`          | Consultar estado de una carga (por uploadId)| Sí                     | `uploadId` (query param)             |
| GET    | `/api/data/status-batch`    | Consultar estado de varias cargas           | Sí                     | `uploadIds` (query param, lista)     |
| GET    | `/api/data/status-all`      | Consultar estado de todas las cargas activas| Sí                     | Ninguno                              |



## Seguridad

- Endpoints protegidos con JWT y roles.
- Solo usuarios autorizados pueden cargar datos o administrar usuarios.

---

## Contacto

Para dudas o contribuciones, contacta a los desarrolladores del proyecto ImgPedia.

---

