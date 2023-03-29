FROM maven:3-amazoncorretto-17

COPY ./pom.xml ./pom.xml

ADD https://github.com/aws-observability/aws-otel-java-instrumentation/releases/latest/download/aws-opentelemetry-agent.jar /opt/aws-opentelemetry-agent.jar
ENV JAVA_TOOL_OPTIONS=-javaagent:/opt/aws-opentelemetry-agent.jar

COPY src ./src/
RUN mvn clean package && cp target/store-spring-1.0.0.jar store-spring.jar

# OpenTelemetry agent configuration
ENV OTEL_TRACES_SAMPLER "always_on"
ENV OTEL_PROPAGATORS "tracecontext,baggage,xray"
ENV OTEL_RESOURCE_ATTRIBUTES "service.name=unicorn-store-spring"
ENV OTEL_IMR_EXPORT_INTERVAL "10000"
ENV OTEL_EXPORTER_OTLP_ENDPOINT "http://localhost:4317"

EXPOSE 80
ENTRYPOINT ["java","-jar","-Dserver.port=80","/store-spring.jar"]
