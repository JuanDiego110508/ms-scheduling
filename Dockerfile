# --- Etapa 1: Build ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# 1. Compilar e instalar la librería común (wd-lib-common)
COPY wd-lib-common ./wd-lib-common
RUN mvn -f wd-lib-common/pom.xml clean install -DskipTests

# 2. Descargar dependencias del microservicio (Caché de capas)
COPY ms-scheduling/pom.xml ./ms-scheduling/ 
RUN mvn -f ms-scheduling/pom.xml dependency:go-offline -B

# 3. Copiar código fuente y empaquetar
COPY ms-scheduling/src ./ms-scheduling/src
RUN mvn -f ms-scheduling/pom.xml clean package -DskipTests

# Etapa final (Runtime)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/ms-scheduling/target/*.jar app.jar

EXPOSE 9097
ENTRYPOINT ["java", "-jar", "app.jar"]