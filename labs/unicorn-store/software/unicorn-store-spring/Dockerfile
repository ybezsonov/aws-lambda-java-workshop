FROM eclipse-temurin:17-jdk
VOLUME /tmp
COPY target/store-spring-1.0.0.jar app.jar
#ENV SPRING_DATASOURCE_PASSWORD=
#ENV SPRING_DATASOURCE_URL=jdbc:postgresql://unicorninstance.chnwulah0hmh.eu-central-1.rds.amazonaws.com:5432/unicorns
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]