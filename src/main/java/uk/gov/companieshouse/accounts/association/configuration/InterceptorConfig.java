package uk.gov.companieshouse.accounts.association.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.companieshouse.accounts.association.interceptor.RequestLifecycleInterceptor;
import uk.gov.companieshouse.accounts.association.interceptor.TokenPermissionsInterceptor;
import uk.gov.companieshouse.accounts.association.service.UsersService;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    private final UsersService usersService;
    private final TokenPermissionsInterceptor tokenPermissionsInterceptor;

    public InterceptorConfig( final UsersService usersService, final TokenPermissionsInterceptor tokenPermissionsInterceptor ) {
        this.usersService = usersService;
        this.tokenPermissionsInterceptor = tokenPermissionsInterceptor;
    }

    @Override
    public void addInterceptors( final InterceptorRegistry registry ) {
        registry.addInterceptor( tokenPermissionsInterceptor )
                .addPathPatterns( "/associations", "/associations/*" );
        registry.addInterceptor( new RequestLifecycleInterceptor( usersService ) );
    }

}