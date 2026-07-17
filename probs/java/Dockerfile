# Stage 1: Build
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Stage 2: Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/esa-java-synthetics-prob-1.0-SNAPSHOT.jar app.jar

ENV JDK_JAVA_OPTIONS="-Xmx4g -Xms2g -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError"

EXPOSE 8000 8080

ENTRYPOINT ["java", "-jar", "app.jar"]