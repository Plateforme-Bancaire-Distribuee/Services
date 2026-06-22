FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/service-loan-3.2.5.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
