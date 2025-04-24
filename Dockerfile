FROM amazoncorretto:21.0.7-alpine3.18 AS builder

WORKDIR /app/imgpedia_backend

COPY ./.mvn ./.mvn
COPY ./mvnw .
COPY ./pom.xml .
COPY ./libs ./libs

# Install dependencies but not build 
RUN ./mvnw clean package -Dmaven.test.skip -Dmaven.main.skip -Dspring-boot.repackage.skip && rm -r ./target/

COPY ./src ./src

#Now build the project
RUN ./mvnw clean package

FROM amazoncorretto:21.0.7-alpine3.18

WORKDIR /app

RUN mkdir ./logs

RUN mkdir -p /nas_mount/imgpedia/resource

COPY --from=builder /app/imgpedia_backend/target/*.jar .

ENV PORT 8081

EXPOSE $PORT

ENTRYPOINT ["java", "-jar", "imgpedia_backend-1.0.0.jar"]