package uk.gov.companieshouse.accounts.association.configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Installs the OpenTelemetry logback appender so that log records are correlated with traces.
 * Spring Boot 4's native OpenTelemetry starter does not wire this up automatically.
 */
@Component
@ConditionalOnProperty( prefix = "management.opentelemetry", name = "enabled", havingValue = "true" )
class OpenTelemetryAppenderInitializer implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    OpenTelemetryAppenderInitializer( final OpenTelemetry openTelemetry ) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install( this.openTelemetry );
    }

}
