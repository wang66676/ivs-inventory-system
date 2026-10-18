FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/app/target/ivs-app-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]

