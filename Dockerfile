FROM amazoncorretto:24.0.1-alpine3.19 AS builder

WORKDIR /app/imgpedia_backend

COPY ./.mvn ./.mvn
COPY ./mvnw .
COPY ./pom.xml .
COPY ./libs ./libs

RUN chmod +x ./mvnw

# Install dependencies but not build 
RUN ./mvnw clean package -Dspring.profiles.active=local -Dmaven.test.skip -Dmaven.main.skip -Dspring-boot.repackage.skip && rm -r ./target/

COPY ./src ./src

#Now build the project
RUN ./mvnw clean package -Dspring.profiles.active=local

FROM amazoncorretto:24.0.1-alpine3.19

WORKDIR /app

RUN mkdir ./logs

RUN mkdir -p /nas_mount/imgpedia/resource

COPY --from=builder /app/imgpedia_backend/target/*.jar .

RUN apk add --no-cache curl tar bash coreutils

RUN curl -L https://archive.apache.org/dist/jena/binaries/apache-jena-fuseki-5.2.0.tar.gz | tar xz -C /opt && \
    mv /opt/apache-jena-fuseki-5.2.0 /opt/apache-jena-5.2.0 && \
    ln -s /opt/apache-jena-5.2.0 /opt/jena

# Instala jq
RUN wget -O /usr/local/bin/jq https://github.com/jqlang/jq/releases/download/jq-1.7.1/jq-linux-amd64 \
    && chmod +x /usr/local/bin/jq

# Asegura que JAVA_HOME y el PATH estén bien para Jena y Java
ENV JAVA_HOME="/usr/lib/jvm/default-jvm"
ENV PATH="/opt/jena/bin:/opt/apache-jena-5.2.0/bin:/usr/local/bin:${PATH}"

ENV PORT 8081

EXPOSE $PORT

# Setting OOM score adjustment and executing the jar
ENTRYPOINT ["/bin/bash", "-c", "echo -1000 > /proc/self/oom_score_adj 2>/dev/null || true; exec java $JAVA_OPTS -jar imgpedia_backend-1.0.0.jar"]
