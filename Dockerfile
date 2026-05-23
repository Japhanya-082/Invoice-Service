FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/Invoice-Service-*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 5671
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
