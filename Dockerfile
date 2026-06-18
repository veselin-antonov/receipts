FROM eclipse-temurin:25-jre-alpine
ARG VERSION

# Install Tesseract OCR and English language data
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng tesseract-ocr-data-bul

WORKDIR /app
COPY /build/libs/receipts-api-${VERSION}.jar app.jar
CMD ["java", "-jar", "app.jar"]