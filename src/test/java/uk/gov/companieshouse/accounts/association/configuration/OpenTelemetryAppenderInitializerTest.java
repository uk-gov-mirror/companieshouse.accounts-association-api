package uk.gov.companieshouse.accounts.association.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag( "unit-test" )
@ExtendWith( MockitoExtension.class )
class OpenTelemetryAppenderInitializerTest {

    @Test
    void afterPropertiesSetInstallsOpenTelemetryAppenderWithoutThrowing() {
        // Arrange
        final var openTelemetry = Mockito.mock( OpenTelemetry.class );
        final var initializer = new OpenTelemetryAppenderInitializer( openTelemetry );

        // Act & Assert
        assertDoesNotThrow( initializer::afterPropertiesSet );
    }

}
