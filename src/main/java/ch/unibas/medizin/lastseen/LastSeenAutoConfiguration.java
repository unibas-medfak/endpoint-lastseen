package ch.unibas.medizin.lastseen;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "endpoint-lastseen", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(LastSeenProperties.class)
public class LastSeenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    EndpointRegistry endpointRegistry() {
        return new EndpointRegistry(Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    JsonFileStore endpointLastSeenStore(LastSeenProperties properties) {
        return new JsonFileStore(properties.getFile());
    }

    @Bean
    LastSeenFlusher endpointLastSeenFlusher(EndpointRegistry registry, JsonFileStore store,
                                            LastSeenProperties properties) {
        return new LastSeenFlusher(registry, store, properties.getFlushInterval());
    }

    @Bean
    EndpointRegistrar endpointRegistrar(EndpointRegistry registry, ApplicationContext context,
                                        LastSeenProperties properties) {
        return new EndpointRegistrar(registry, context, properties.getExcludePatterns());
    }

    @Bean
    WebMvcConfigurer endpointLastSeenConfigurer(EndpointRegistry registry, LastSeenProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry interceptors) {
                interceptors.addInterceptor(new LastSeenInterceptor(registry))
                        .excludePathPatterns(properties.getExcludePatterns());
            }
        };
    }
}
