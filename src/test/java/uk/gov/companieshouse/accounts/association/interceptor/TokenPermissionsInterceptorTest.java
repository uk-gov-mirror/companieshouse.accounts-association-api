package uk.gov.companieshouse.accounts.association.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.companieshouse.accounts.association.interceptor.TokenPermissionsInterceptor.COMPANY_UPGRADED_AUTH_VALID_UNTIL;
import static uk.gov.companieshouse.accounts.association.interceptor.TokenPermissionsInterceptor.ERIC_AUTHORISED_TOKEN_PERMISSIONS;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag( "unit-test" )
class TokenPermissionsInterceptorTest {

    private final TokenPermissionsInterceptor tokenPermissionsInterceptor = new TokenPermissionsInterceptor();

    @Test
    void preHandleWithPostAssociationsAndMissingTokenPermissionReturnsForbidden() {
        final var request = new MockHttpServletRequest();
        request.addHeader( "X-Request-Id", "theId123" );
        request.setMethod( "POST" );
        request.setRequestURI( "/associations" );

        final var response = new MockHttpServletResponse();

        final var result = tokenPermissionsInterceptor.preHandle( request, response, new Object() );

        assertFalse( result );
        assertEquals( 401, response.getStatus() );
    }

    @Test
    void preHandleWithPatchAssociationsAndRequiredTokenPermissionReturnsTrue() {
        final var request = new MockHttpServletRequest();
        request.addHeader( "X-Request-Id", "theId123" );
        request.addHeader( ERIC_AUTHORISED_TOKEN_PERMISSIONS,
                COMPANY_UPGRADED_AUTH_VALID_UNTIL + "=2999-01-01T00:00:00Z" );
        request.setMethod( "PATCH" );
        request.setRequestURI( "/associations/abc123" );

        final var response = new MockHttpServletResponse();

        final var result = tokenPermissionsInterceptor.preHandle( request, response, new Object() );

        assertTrue( result );
        assertEquals( 200, response.getStatus() );
    }

    @Test
    void preHandleWithPostAssociationsAndExpiredTokenPermissionReturnsForbidden() {
        final var request = new MockHttpServletRequest();
        request.addHeader( "X-Request-Id", "theId123" );
        request.addHeader( ERIC_AUTHORISED_TOKEN_PERMISSIONS,
                COMPANY_UPGRADED_AUTH_VALID_UNTIL + "=2000-01-01T00:00:00Z" );
        request.setMethod( "POST" );
        request.setRequestURI( "/associations" );

        final var response = new MockHttpServletResponse();

        final var result = tokenPermissionsInterceptor.preHandle( request, response, new Object() );

        assertFalse( result );
        assertEquals( 401, response.getStatus() );
    }

    @Test
    void preHandleWithNonReauthenticationEndpointReturnsTrue() {
        final var request = new MockHttpServletRequest();
        request.setMethod( "GET" );
        request.setRequestURI( "/associations" );

        final var response = new MockHttpServletResponse();

        final var result = tokenPermissionsInterceptor.preHandle( request, response, new Object() );

        assertTrue( result );
        assertEquals( 200, response.getStatus() );
    }
}

