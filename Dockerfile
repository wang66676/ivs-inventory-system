FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl \
    && addgroup -S ivs \
    && adduser -S ivs -G ivs
COPY --chown=ivs:ivs backend/app/target/ivs-app-0.1.0-SNAPSHOT.jar app.jar
USER ivs
EXPOSE 8090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]

