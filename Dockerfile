FROM openjdk:21-jdk-slim
ARG VERSION
WORKDIR /app
COPY /build/libs/receipts-api-${VERSION}.jar app.jar
CMD ["java", "-jar", "app.jar"]