# ---- Build stage: a teljes reactorbĂłl csak ezt a modult buildeljĂĽk ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper Ă©s POM-ok elĹ‘bb, hogy a fĂĽggĹ‘sĂ©g-rĂ©teg cache-elĹ‘djĂ¶n.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY gateway/pom.xml gateway/pom.xml
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/learning-service/pom.xml services/learning-service/pom.xml
COPY services/social-service/pom.xml services/social-service/pom.xml
COPY services/ai-service/pom.xml services/ai-service/pom.xml
RUN chmod +x mvnw && ./mvnw -q -N dependency:go-offline || true

# ForrĂˇs bemĂˇsolĂˇsa Ă©s a modul (+ fĂĽggĹ‘sĂ©gei) csomagolĂˇsa tesztek nĂ©lkĂĽl.
COPY . .
RUN ./mvnw -q -pl :ai-service -am clean package -DskipTests

# ---- Runtime stage: slim JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd -r -u 1001 appuser
COPY --from=build /workspace/services/ai-service/target/ai-service.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]