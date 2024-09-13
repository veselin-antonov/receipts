FROM openjdk:21-jdk-slim
WORKDIR /app
COPY /build/libs/receipts-api-0.0.3.jar app.jar
CMD ["java", "-jar", "app.jar"]