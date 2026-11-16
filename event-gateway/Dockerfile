FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml /workspace/pom.xml
COPY account-service/pom.xml /workspace/account-service/pom.xml
COPY event-gateway/pom.xml /workspace/event-gateway/pom.xml
COPY account-service/src /workspace/account-service/src
COPY event-gateway/src /workspace/event-gateway/src
WORKDIR /workspace
RUN mvn -pl event-gateway -am clean package -DskipTests
FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/event-gateway/target/event-gateway-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
