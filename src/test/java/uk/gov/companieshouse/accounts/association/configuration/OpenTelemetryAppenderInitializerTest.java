package uk.gov.companieshouse.accounts.association.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Tag( "unit-test" )
@ExtendWith( MockitoExtension.class )
class OpenTelemetryAppenderInitializerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration( OpenTelemetryTestConfig.class, OpenTelemetryAppenderInitializer.class );

    @Test
    void afterPropertiesSetDelegatesToInstallAppenderWithConfiguredOpenTelemetryInstance() {
        // Arrange
        final var openTelemetry = mock( OpenTelemetry.class );
        final var initializer = spy( new OpenTelemetryAppenderInitializer( openTelemetry ) );
        doNothing().when( initializer ).installAppender( openTelemetry );

        // Act
        initializer.afterPropertiesSet();

        // Assert
        verify( initializer ).installAppender( openTelemetry );
    }

    @Test
    void beanIsNotRegisteredWhenManagementOpentelemetryEnabledIsAbsent() {
        contextRunner.run( context -> assertThat( context ).doesNotHaveBean( OpenTelemetryAppenderInitializer.class ) );
    }

    @Test
    void beanIsNotRegisteredWhenManagementOpentelemetryEnabledIsFalse() {
        contextRunner.withPropertyValues( "management.opentelemetry.enabled=false" )
                .run( context -> assertThat( context ).doesNotHaveBean( OpenTelemetryAppenderInitializer.class ) );
    }

    @Test
    void beanIsRegisteredWhenManagementOpentelemetryEnabledIsTrue() {
        contextRunner.withPropertyValues( "management.opentelemetry.enabled=true" )
                .run( context -> assertThat( context ).hasSingleBean( OpenTelemetryAppenderInitializer.class ) );
    }

    @Configuration
    static class OpenTelemetryTestConfig {

        @Bean
        OpenTelemetry openTelemetry() {
            return mock( OpenTelemetry.class );
        }

    }

}
