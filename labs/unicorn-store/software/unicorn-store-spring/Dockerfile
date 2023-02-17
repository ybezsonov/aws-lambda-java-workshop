FROM eclipse-temurin:17-jdk
VOLUME /tmp
COPY target/store-spring-1.0.0.jar app.jar
EXPOSE 80
ENTRYPOINT ["java","-jar","-Dserver.port=80","/app.jar"]