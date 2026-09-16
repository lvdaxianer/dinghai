FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/knowledge-assistant-*.jar /app/knowledge-assistant.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/knowledge-assistant.jar"]
