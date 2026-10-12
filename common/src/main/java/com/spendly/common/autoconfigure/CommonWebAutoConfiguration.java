package com.spendly.common.autoconfigure;

import com.spendly.common.security.CurrentUserArgumentResolver;
import com.spendly.common.security.OwnershipPermissionEvaluator;
import com.spendly.common.security.ResourceOwnership;
import com.spendly.common.security.StepUpAuthentication;
import com.spendly.common.web.CorrelationIdFilter;
import com.spendly.common.web.GlobalExceptionHandler;
import com.spendly.common.web.OptimisticLockAdvice;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the shared web concerns into any servlet service that puts {@code common} on its classpath - no per-service
 * configuration class needed.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.dao.OptimisticLockingFailureException")
    static class OptimisticLocking {

        @Bean
        @ConditionalOnMissingBean
        OptimisticLockAdvice optimisticLockAdvice(GlobalExceptionHandler handler) {
            return new OptimisticLockAdvice(handler);
        }
    }

    /**
     * Registered ahead of the security filter chain so that authentication failures are logged with a correlation id
     * too.
     */
    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(
                new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.Jwt")
    @ConditionalOnMissingBean(PermissionEvaluator.class)
    public OwnershipPermissionEvaluator ownershipPermissionEvaluator(ObjectProvider<ResourceOwnership> resources) {
        return new OwnershipPermissionEvaluator(resources.orderedStream().toList());
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.Jwt")
    @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator permissionEvaluator,
            ObjectProvider<RoleHierarchy> roleHierarchy, ObjectProvider<GrantedAuthorityDefaults> authorityDefaults,
            ApplicationContext context) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        roleHierarchy.ifAvailable(handler::setRoleHierarchy);
        authorityDefaults.ifAvailable(defaults -> handler.setDefaultRolePrefix(defaults.getRolePrefix()));
        handler.setApplicationContext(context);
        return handler;
    }

    @Bean(name = "stepUp")
    @ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.Jwt")
    @ConditionalOnMissingBean(StepUpAuthentication.class)
    public StepUpAuthentication stepUp(@Value("${spendly.step-up.max-age:PT15M}") Duration maxAge,
            ObjectProvider<Clock> clock) {
        return new StepUpAuthentication(maxAge, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.Jwt")
    public WebMvcConfigurer currentUserWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }
}
