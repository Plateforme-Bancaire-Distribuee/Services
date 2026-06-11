FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copie le jar buildé
COPY target/customer-service-1.0.0.jar app.jar

# Variables d'environnement par défaut (surchargeables au déploiement)
ENV DB_USERNAME=postgres \
    DB_PASSWORD=postgres \
    JWT_SECRET=4a8f2e1b9d3c7f5a6e2d8b4c1f9e3a7d2b5c8f4e1a6d9b3c7f2e5a8d1b4c9f3e \
    KAFKA_SERVERS=localhost:9092 \
    EUREKA_URL=http://localhost:8761/eureka \
    DOCUMENT_SERVICE_URL=http://localhost:8082

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]