FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S stocksync && adduser -S stocksync -G stocksync

WORKDIR /app
COPY --from=build /workspace/target/StockSync-0.0.1-SNAPSHOT.jar app.jar

USER stocksync
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
