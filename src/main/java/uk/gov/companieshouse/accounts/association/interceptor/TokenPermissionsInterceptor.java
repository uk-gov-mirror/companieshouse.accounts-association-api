package uk.gov.companieshouse.accounts.association.interceptor;

import static uk.gov.companieshouse.accounts.association.models.Constants.X_REQUEST_ID;
import static uk.gov.companieshouse.accounts.association.utils.LoggingUtil.LOGGER;
import static uk.gov.companieshouse.api.util.security.RequestUtils.getRequestHeader;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TokenPermissionsInterceptor implements HandlerInterceptor {

    public static final String ERIC_AUTHORISED_TOKEN_PERMISSIONS = "ERIC-Authorised-Token-Permissions";
    public static final String COMPANY_UPGRADED_AUTH_VALID_UNTIL = "company_upgraded_auth_valid_until";



    private boolean isReauthenticationEndpoint( final HttpServletRequest request ) {
        final var method = request.getMethod();
        return "PATCH".equalsIgnoreCase( method );
    }

    private Optional<Instant> parseCompanyUpgradedAuthValidUntil( final String tokenPermissions ) {
        final var pattern = Pattern.compile( COMPANY_UPGRADED_AUTH_VALID_UNTIL + "\\s*=\\s*([^\\s]+)" );
        final var matcher = pattern.matcher( tokenPermissions );
        if ( !matcher.find() ) {
            return Optional.empty();
        }

        final var expiryTime = matcher.group( 1 );
        if ( expiryTime == null || expiryTime.isBlank() ) {
            return Optional.empty();
        }

        try {
            // Try ISO-8601 format first
            return Optional.of( Instant.parse( expiryTime ) );
        } catch ( DateTimeParseException ignored ) {
            // Fall back to Unix timestamp (seconds since epoch)
            try {
                final var unixSeconds = Long.parseLong( expiryTime );
                return Optional.of( Instant.ofEpochSecond( unixSeconds ) );
            } catch ( NumberFormatException numberIgnored ) {
                return Optional.empty();
            }
        }
    }

    private boolean hasCompanyUpgradedAuthTokenPermission( final HttpServletRequest request ) {
        final var xRequestId = getRequestHeader( request, X_REQUEST_ID );
        return Optional.ofNullable( getRequestHeader( request, ERIC_AUTHORISED_TOKEN_PERMISSIONS ) )
                .map( tokenPermissions -> parseCompanyUpgradedAuthValidUntil( tokenPermissions )
                        .map( permissionExpiry -> {
                            if ( permissionExpiry.isBefore( Instant.now() ) ) {
                                LOGGER.infoContext( xRequestId,
                                        "company_upgraded_auth_valid_until is present but expired.",
                                        null );
                                return false;
                            }
                            return true;
                        } )
                        .orElseGet( () -> {
                            LOGGER.infoContext( xRequestId,
                                    "company_upgraded_auth_valid_until is present but malformed or missing a value.",
                                    null );
                            return false;
                        } ) )
                .orElseGet( () -> {
                    LOGGER.infoContext( xRequestId,
                            "ERIC-Authorised-Token-Permissions header is missing.",
                            null );
                    return false;
                } );
    }

    @Override
    public boolean preHandle( final HttpServletRequest request, final HttpServletResponse response, final Object handler ) {
        if ( isReauthenticationEndpoint( request ) && !hasCompanyUpgradedAuthTokenPermission( request ) ) {
            LOGGER.infoContext( getRequestHeader( request, X_REQUEST_ID ),
                    "Request does not contain a valid company_upgraded_auth_valid_until token permission.",
                    null );
            response.setStatus( 401 );
            return false;
        }
        return true;
    }
}

