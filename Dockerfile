FROM eclipse-temurin:21-jre-alpine
ARG VERSION
WORKDIR /app
COPY /build/libs/receipts-api-${VERSION}.jar app.jar
CMD ["java", "-jar", "app.jar"]