# IMGpedia Backend

## Índice

- [Introducción](#introducción)  
- [Responsabilidades principales](#responsabilidades-principales)  
- [Estructura del proyecto](#estructura-del-proyecto)  
- [Tecnologías principales](#tecnologías-principales)  
- [Levantamiento en Ambiente Local](#levantamiento-en-ambiente-local)  
- [Levantamiento en Ambiente de Producción](#levantamiento-en-ambiente-producción)
- [Seguridad](#seguridad)  
- [Contacto](#contacto)  


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
- [Apache Jena 5.2 extendido con operaciones de similitud y clustering](https://github.com/scferrada/jena)
- [Spring Security 3.4.4](https://spring.io/projects/spring-security)
- [Docker](https://www.docker.com/)

---

## Levantamiento en Ambiente Local

1. Para desarrollar y probar nuevas funcionalidades en tu entorno local, ejecuta el siguiente comando desde la raíz del proyecto:

En Windows
```ps
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Linux (distribuciones basadas en Unix)
```sh
mvn spring-boot:run -Dspring-boot.run.profiles=local
```


2. Inicia sesión con el usuario `admin` y la contraseña `admin`. Guarda el token Bearer obtenido, ya que será necesario para acceder a los endpoints que requieren autenticación.

```sh
curl --location 'http://localhost:8081/api/auth/login' \
--header 'Content-Type: application/json' \
--data '{"username":"admin","password":"admin"}'
```

3. Subir datos RDF hacia la base de datos usando el endpoint `/api/data/upload`.  

```sh
curl --location 'localhost:8081/api/data/upload' \
--form 'file=@"ruta/hacia/tu/archivo/"'
```
Puedes consultar el estado de la carga utilizando el endpoint `/api/data/status`.

4. Haz consultas SPARQL en el endpoint `api/sparql/query`, por ejemplo: 

```sh 
curl --location 'localhost:8081/api/sparql/query' \
--header 'Content-Type: application/json' \
--data '{
  "query": "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100",
  "format": "json",
  "timeout": 0,
  "clientQueryId": 1
}'
```

---
## Levantamiento en Ambiente Producción

Para desplegar en ambiente de producción, es necesario ejecutar el workflow `build & deploy` disponible en la sección de **Actions** del repositorio de GitHub. Este workflow compila el proyecto, ejecuta las pruebas y realiza el despliegue automático en el entorno configurado. Asegúrate de tener los permisos necesarios para ejecutar acciones en el repositorio.

Para ejecutar el action, haz clic en el botón "Run workflow" y asegúrate de seleccionar la rama donde se encuentran tus cambios. Como buena práctica, primero prueba los cambios en el ambiente de desarrollo, luego haz merge con la rama `main` y finalmente ejecuta el workflow para desplegar en producción.

### Imagen Docker

La imagen Docker del backend se encuentra disponible en [Docker Hub](https://hub.docker.com/repository/docker/elemma00/imgpedia_backend/). Los cambios y actualizaciones del backend se publican en este repositorio, permitiendo su despliegue sencillo en cualquier entorno compatible con Docker.


---

## Endpoints disponibles

### Consultas

| Método | Endpoint              | Descripción                              | Autenticación requerida | Parámetros requeridos                |
|--------|-----------------------|------------------------------------------|------------------------|--------------------------------------|
| POST   | `/api/sparql/query`   | Ejecutar consulta SPARQL                 | No                     | Body JSON: `query`                   |
| POST   | `/api/sparql/query/stop` | Detener una consulta SPARQL en ejecución | No                     | Body JSON: identificador de consulta |

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

Para dudas o contribuciones, contactar con desarrolladores del proyecto IMGpedia.
- `scferrada`
- `Elemma00`
---

