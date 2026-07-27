FROM eclipse-temurin:25
COPY "build/libs/sigil-*SNAPSHOT.jar" app.jar
COPY "application.yml" application.yml
ENTRYPOINT ["java","-jar","/app.jar"]